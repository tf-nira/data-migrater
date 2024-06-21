package io.mosip.packet.extractor.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.GlobalConfig;
import io.mosip.packet.core.constant.tracker.TrackerStatus;
import io.mosip.packet.core.dto.dbimport.PacketCreatorResponse;
import io.mosip.packet.core.dto.tracker.TrackerRequestDto;
import io.mosip.packet.core.dto.upload.PacketUploadDTO;
import io.mosip.packet.core.dto.upload.PacketUploadResponseDTO;
import io.mosip.packet.core.entity.PacketTracker;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.repository.PacketTrackerRepository;
import io.mosip.packet.core.service.thread.*;
import io.mosip.packet.core.util.TrackerUtil;
import io.mosip.packet.core.util.regclient.ConfigUtil;
import io.mosip.packet.uploader.service.PacketUploaderService;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
public class Reprocessor {

    @Autowired
    private PacketTrackerRepository packetTrackerRepository;

    @Value("${mosip.packet.creater.max-threadpool-count:1}")
    private Integer maxThreadPoolCount;

    @Value("${mosip.packet.creater.max-records-process-per-threadpool:100}")
    private Integer maxRecordsCountPerThreadPool;

    @Value("${mosip.packet.creater.max-thread-execution-count:100}")
    private Integer maxThreadExecCount;

    @Value("${packet.manager.account.name}")
    private String packetUploadPath;

    @Value("${mosip.selected.languages}")
    private String primaryLanguage;

    @Value("${mosip.packet.uploader.enable:true}")
    private boolean enablePaccketUploader;

    @Value("${mosip.packet.reprocess.status.list}")
    private String reprocessStatusList;

    @Autowired
    PacketUploaderService packetUploaderService;

    @Autowired
    TrackerUtil trackerUtil;

    private ObjectMapper objectMapper = new ObjectMapper();

    private static final Logger LOGGER = DataProcessLogger.getLogger(Reprocessor.class);

    public void reprocess() throws IOException, ParseException, InterruptedException {
        PacketCreatorResponse packetCreatorResponse = new PacketCreatorResponse();
        packetCreatorResponse.setRID(new ArrayList<>());

        ResultSetter setter = new ResultSetter() {
            @SneakyThrows
            @Override
            public void setResult(Object obj) {
                ResultDto resultDto = (ResultDto) obj;
                packetCreatorResponse.getRID().add(resultDto.getRegNo());
                TrackerRequestDto trackerRequestDto = new TrackerRequestDto();
                trackerRequestDto.setRegNo(resultDto.getRegNo());
                trackerRequestDto.setRefId(resultDto.getRefId());
                if (enablePaccketUploader) {
                    trackerRequestDto.setStatus(TrackerStatus.PROCESSED.toString());
                } else {
                    trackerRequestDto.setStatus(TrackerStatus.PROCESSED_WITHOUT_UPLOAD.toString());
                }
                trackerUtil.addTrackerEntry(trackerRequestDto);
                trackerUtil.addTrackerLocalEntry(resultDto.getRefId(), null, (enablePaccketUploader ? TrackerStatus.PROCESSED : TrackerStatus.PROCESSED_WITHOUT_UPLOAD), null, enablePaccketUploader ? null : null, null, null);
            }
        };

        CustomizedThreadPoolExecutor threadPool = new CustomizedThreadPoolExecutor(maxThreadPoolCount, maxRecordsCountPerThreadPool, maxThreadExecCount, "RE-PROCESSOR");

        List<String> statusList = Arrays.asList(reprocessStatusList.split(","));

        List<PacketTracker> trackerList =  packetTrackerRepository.findByStatusIn(statusList);

        for(PacketTracker packetTracker : trackerList) {
            ThreadReprocessorController controller = new ThreadReprocessorController();
            controller.setPacketTracker(packetTracker);
            controller.setSetter(setter);
            controller.setProcessor(new ThreadReprocessor() {
                @Override
                public void processData(PacketTracker packetTracker) throws Exception {
                    if(packetTracker.getStatus().equals(TrackerStatus.CREATED.toString()) || packetTracker.getStatus().equals(TrackerStatus.PROCESSED_WITHOUT_UPLOAD.toString())) {
                        HashMap<String, Object> demoDetails = objectMapper.readValue(packetTracker.getRequest().toString(), new TypeReference<HashMap<String, Object>>() {});

                        Path identityFile = Paths.get(System.getProperty("user.dir"), "identity.json");

                        if (identityFile.toFile().exists()) {
                            PacketUploadDTO uploadDTO = new PacketUploadDTO();

                            JSONParser parser = new JSONParser();
                            JSONObject jsonObject = (JSONObject) parser.parse(IOUtils.toString(new FileInputStream(identityFile.toFile()), StandardCharsets.UTF_8));
                            JSONObject identityJsonObject = (JSONObject) jsonObject.get("identity");
                            for (Object entry : identityJsonObject.keySet()) {
                                String val = (String) ((JSONObject)identityJsonObject.get(entry)).get("value");
                                if (val.contains(",")) {
                                    String[] valList = val.split(",");
                                    String fullVal = null;

                                    for (String val2 : valList) {
                                        if(fullVal == null) {
                                            fullVal= (String) demoDetails.get(val2);
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
                            uploadDTO.setRegistrationType(packetTracker.getProcess());
                            uploadDTO.setPacketId(packetTracker.getRegNo());
                            uploadDTO.setRegistrationId(packetTracker.getRegNo().split("-")[0]);
                            uploadDTO.setLangCode(primaryLanguage);

                            List<PacketUploadDTO> uploadList = new ArrayList<>();
                            uploadList.add(uploadDTO);
                            HashMap<String, PacketUploadResponseDTO> response = new HashMap<>();

                            if(enablePaccketUploader) {
                                packetUploaderService.syncPacket(uploadList, ConfigUtil.getConfigUtil().getCenterId(), ConfigUtil.getConfigUtil().getMachineId(), response);
                                trackerUtil.addTrackerLocalEntry(packetTracker.getRefId(), packetTracker.getRegNo(), TrackerStatus.SYNCED, null, objectMapper.writeValueAsBytes(uploadList), null, null);
                                packetUploaderService.uploadSyncedPacket(uploadList, response);
                            } else {
                                LOGGER.warn("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Packet Uploader Disabled : "+ (new Gson()).toJson(response));
                            }

                            ResultDto resultDto = new ResultDto();
                            resultDto.setRegNo(packetTracker.getRegNo());
                            resultDto.setRefId(packetTracker.getRefId());
                            setter.setResult(resultDto);
                            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Packet Upload Response : "+ (new Gson()).toJson(response));
                        } else {
                            throw new Exception("Identity Mapping JSON File missing");
                        }
                    } else if(packetTracker.getStatus().equals(TrackerStatus.SYNCED.toString())) {
                        List<PacketUploadDTO> uploadList = objectMapper.readValue(packetTracker.getRequest().toString(), new TypeReference<List<PacketUploadDTO>>() {});
                        HashMap<String, PacketUploadResponseDTO> response = new HashMap<>();

                        if(enablePaccketUploader) {
                            packetUploaderService.uploadSyncedPacket(uploadList, response);
                        } else {
                            LOGGER.warn("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Packet Uploader Disabled : "+ (new Gson()).toJson(response));
                        }

                        ResultDto resultDto = new ResultDto();
                        resultDto.setRegNo(packetTracker.getRegNo());
                        resultDto.setRefId(packetTracker.getRefId());
                        setter.setResult(resultDto);
                        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Packet Upload Response : "+ (new Gson()).toJson(response));
                    }  else if(packetTracker.getStatus().equals(TrackerStatus.STARTED.toString())) {

                    }
                }
            });
            threadPool.ExecuteTask(controller);
        }
        threadPool.setInputProcessCompleted(true);

        do {
            Thread.sleep(15000);
        } while(!GlobalConfig.isThreadPoolCompleted("RE-PROCESSOR"));
    }
}
