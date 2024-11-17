package io.mosip.packet.data.datapostprocessor;

import com.google.gson.Gson;
import io.mosip.commons.packet.dto.PacketInfo;
import io.mosip.commons.packet.dto.packet.PacketDto;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.GlobalConfig;
import io.mosip.packet.core.constant.activity.ActivityName;
import io.mosip.packet.core.constant.tracker.TrackerStatus;
import io.mosip.packet.core.dto.DataPostProcessorResponseDto;
import io.mosip.packet.core.dto.DataProcessorResponseDto;
import io.mosip.packet.core.dto.upload.PacketUploadDTO;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.service.thread.ResultDto;
import io.mosip.packet.core.service.thread.ResultSetter;
import io.mosip.packet.core.spi.datapostprocessor.DataPostProcessor;
import io.mosip.packet.core.util.CommonUtil;
import io.mosip.packet.core.util.TrackerUtil;
import io.mosip.packet.data.dataexporter.MosipPacketExporter;
import io.mosip.packet.manager.service.PacketCreatorService;
import io.mosip.packet.manager.util.PacketCreator;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.mosip.packet.core.constant.GlobalConfig.SESSION_KEY;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
public class MOSIPPostProcessor implements DataPostProcessor {
    private static final Logger LOGGER = DataProcessLogger.getLogger(MOSIPPostProcessor.class);

    @Autowired
    PacketCreatorService packetCreatorService;

    @Autowired
    private TrackerUtil trackerUtil;

    @Value("${packet.manager.account.name}")
    private String packetUploadPath;

    @Value("${mosip.selected.languages}")
    private String primaryLanguage;

    @Value("${mosip.packet.uploader.enable:true}")
    private boolean enablePacketUpload;

    @Override
    public DataPostProcessorResponseDto postProcess(DataProcessorResponseDto processObject, ResultSetter setter, Long processStartTime) throws Exception {
        DataPostProcessorResponseDto responseDto = new DataPostProcessorResponseDto();
        responseDto.setProcess(processObject.getProcess());
        responseDto.setRefId(processObject.getRefId());
        responseDto.setTrackerRefId(processObject.getTrackerRefId());
        responseDto.setResponses(new HashMap<>());

        PacketDto packetDto = (PacketDto) processObject.getResponses().get("packetDto");
        HashMap<String, Object> demoDetails = (HashMap<String, Object>) processObject.getResponses().get("demoDetails");

        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "PacketDto Response " +  (new Gson()).toJson(packetDto));
        String trackerRefId = processObject.getTrackerRefId();
        List<PacketInfo> infoList = packetCreatorService.persistPacket(packetDto);
        PacketInfo info = infoList.get(0);

        Long timeDifference = System.nanoTime()-processStartTime;
        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken for Packet Creation in Local Storage " + trackerRefId + " " + TimeUnit.MILLISECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));

        trackerUtil.addTrackerLocalEntry(processObject.getRefId(), info.getId(), TrackerStatus.CREATED, processObject.getProcess(), demoDetails, SESSION_KEY, GlobalConfig.getActivityName());

        Path identityFile = Paths.get(System.getProperty("user.dir"), "identity.json");

        if (identityFile.toFile().exists()) {
            PacketUploadDTO uploadDTO = new PacketUploadDTO();

            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(IOUtils.toString(new FileInputStream(identityFile.toFile()), StandardCharsets.UTF_8));
            JSONObject identityJsonObject = (JSONObject) jsonObject.get("identity");
            for (Object entry : identityJsonObject.keySet()) {
                String val = (String) ((JSONObject) identityJsonObject.get(entry)).get("value");
                if (val.contains(",")) {
                    String[] valList = val.split(",");
                    String fullVal = null;

                    for (String val2 : valList) {
                        if (fullVal == null) {
                            fullVal = (String) demoDetails.get(val2);
                        } else {
                            fullVal += " " + demoDetails.get(val2);
                        }
                    }
                    uploadDTO.setValue(entry.toString(), fullVal);
                } else {
                    uploadDTO.setValue(entry.toString(), demoDetails.get(entry));
                }
            }

            Path path = Paths.get(System.getProperty("user.dir"), "home/" + packetUploadPath);
            uploadDTO.setPacketPath(path.toAbsolutePath().toString());
            uploadDTO.setRegistrationType(processObject.getProcess());
            uploadDTO.setPacketId(info.getId());
            uploadDTO.setRegistrationId(info.getId().split("-")[0]);
            uploadDTO.setLangCode(primaryLanguage);

            timeDifference = System.nanoTime()-processStartTime;
            LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken for Completion of Data Process Function " + trackerRefId + " " + TimeUnit.MILLISECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));

            if (enablePacketUpload) {
                responseDto.getResponses().put("uploadDTO", uploadDTO);
                trackerUtil.addTrackerLocalEntry(processObject.getRefId(), info.getId(), TrackerStatus.READY_TO_SYNC, null, responseDto, SESSION_KEY, GlobalConfig.getActivityName());
            } else {
                responseDto.getResponses().put("message", "Successfully Inserted into Packet Tracker Table with Status PROCESSED_WITHOUT_UPLOAD");
                LOGGER.warn("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Packet Uploader Disabled : " + trackerRefId);
                ResultDto resultDto = new ResultDto();
                resultDto.setRegNo(info.getId());
                resultDto.setRefId(processObject.getRefId());
                resultDto.setComments("Packet Created");
                resultDto.setStatus(TrackerStatus.PROCESSED_WITHOUT_UPLOAD);
                setter.setResult(resultDto);
                timeDifference = System.nanoTime()-processStartTime;
                LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken to Stored Packet Information in Local Table for Export OPeration " + trackerRefId + " " + TimeUnit.MILLISECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));
            }
        } else {
            throw new Exception("Identity Mapping JSON File missing");
        }

        return responseDto;
    }
}
