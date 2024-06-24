package io.mosip.packet.data.dataexporter;

import io.mosip.commons.packet.dto.PacketInfo;
import io.mosip.commons.packet.dto.packet.PacketDto;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.GlobalConfig;
import io.mosip.packet.core.constant.activity.ActivityName;
import io.mosip.packet.core.constant.tracker.TrackerStatus;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.dto.upload.PacketUploadDTO;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.service.thread.ResultDto;
import io.mosip.packet.core.service.thread.ResultSetter;
import io.mosip.packet.core.spi.dataexporter.DataExporter;
import io.mosip.packet.core.util.CommonUtil;
import io.mosip.packet.core.util.TrackerUtil;
import io.mosip.packet.core.util.regclient.ConfigUtil;
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
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static io.mosip.packet.core.constant.GlobalConfig.SESSION_KEY;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
public class MosipPacketExporter implements DataExporter {
    private static final Logger LOGGER = DataProcessLogger.getLogger(MosipPacketExporter.class);

    @Autowired
    private PacketCreator packetCreator;

    @Autowired
    private CommonUtil commonUtil;

    @Autowired
    PacketCreatorService packetCreatorService;

    @Autowired
    private TrackerUtil trackerUtil;

    @Value("${packet.manager.account.name}")
    private String packetUploadPath;

    @Value("${mosip.selected.languages}")
    private String primaryLanguage;

    @Override
    public Object export(PacketDto packetDto, DBImportRequest dbImportRequest, HashMap<String, String> metaInfo, HashMap<String, Object> demoDetails,
                         String trackerColumn, ResultSetter setter, String trackerRefId, Long processStartTime) throws Exception {
        packetDto.setRefId(ConfigUtil.getConfigUtil().getCenterId() + "_" + ConfigUtil.getConfigUtil().getMachineId());
        packetCreator.setMetaData(metaInfo, packetDto, dbImportRequest);
        packetDto.setMetaInfo(metaInfo);
        packetDto.setAudits(packetCreator.setAudits(packetDto.getId()));

        Long timeDifference = System.nanoTime()-processStartTime;
        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken for Completion of Audit Log set Process " + trackerRefId + " " + TimeUnit.SECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));

        HashMap<String, Object> idSchema = commonUtil.getLatestIdSchema();
        packetDto.setSchemaJson(idSchema.get("schemaJson").toString());
        packetDto.setOfflineMode(true);

        timeDifference = System.nanoTime()-processStartTime;
        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken for Fetching Latest ID Schema " + trackerRefId + " " + TimeUnit.SECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));

        List<PacketInfo> infoList = packetCreatorService.persistPacket(packetDto);
        PacketInfo info = infoList.get(0);

        timeDifference = System.nanoTime()-processStartTime;
        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken for Packet Creation in Local Storage " + trackerRefId + " " + TimeUnit.SECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));

        trackerUtil.addTrackerLocalEntry(demoDetails.get(trackerColumn).toString(), info.getId(), TrackerStatus.CREATED, dbImportRequest.getProcess(), demoDetails, SESSION_KEY, GlobalConfig.getActivityName());

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
            uploadDTO.setRegistrationType(dbImportRequest.getProcess());
            uploadDTO.setPacketId(info.getId());
            uploadDTO.setRegistrationId(info.getId().split("-")[0]);
            uploadDTO.setLangCode(primaryLanguage);

            timeDifference = System.nanoTime()-processStartTime;
            LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken for Completion of Data Process Function " + trackerRefId + " " + TimeUnit.SECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));

            if (GlobalConfig.getApplicableActivityList().contains(ActivityName.DATA_EXPORTER)) {
                //                                  packetUploaderService.syncPacket(uploadList, ConfigUtil.getConfigUtil().getCenterId(), ConfigUtil.getConfigUtil().getMachineId(), response);
                trackerUtil.addTrackerLocalEntry(demoDetails.get(trackerColumn).toString(), info.getId(), TrackerStatus.READY_TO_SYNC, null, uploadDTO, SESSION_KEY, GlobalConfig.getActivityName());
                //                                  packetUploaderService.uploadSyncedPacket(uploadList, response);
            } else {
                LOGGER.warn("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Packet Uploader Disabled : " + demoDetails.get(trackerColumn).toString());
                ResultDto resultDto = new ResultDto();
                resultDto.setRegNo(info.getId());
                resultDto.setRefId(demoDetails.get(trackerColumn).toString());
                resultDto.setComments("Packet Created");
                resultDto.setStatus(TrackerStatus.PROCESSED_WITHOUT_UPLOAD);
                setter.setResult(resultDto);
                timeDifference = System.nanoTime()-processStartTime;
                LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken to Stored Packet Information in Local Table for Export OPeration " + trackerRefId + " " + TimeUnit.SECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));

            }
        } else {
            throw new Exception("Identity Mapping JSON File missing");
        }

        return null;
    }
}
