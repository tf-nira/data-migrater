package io.mosip.packet.data.datapostprocessor;

import io.mosip.commons.packet.dto.packet.PacketDto;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.FieldCategory;
import io.mosip.packet.core.constant.tracker.TrackerStatus;
import io.mosip.packet.core.dto.DataProcessorResponseDto;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.service.thread.ResultDto;
import io.mosip.packet.core.service.thread.ResultSetter;
import io.mosip.packet.core.spi.QualityWriterFactory;
import io.mosip.packet.core.spi.dataexporter.DataExporterApiFactory;
import io.mosip.packet.core.spi.dataprocessor.DataProcessor;
import io.mosip.packet.core.util.CommonUtil;
import io.mosip.packet.core.util.TrackerUtil;
import io.mosip.packet.core.util.regclient.ConfigUtil;
import io.mosip.packet.manager.util.PacketCreator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.mosip.packet.core.constant.GlobalConfig.*;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
public class MosipPacketDTOProcessor implements DataProcessor {

    @Value("${mosip.extractor.application.id.column:}")
    private String applicationIdColumn;

    private static final Logger LOGGER = DataProcessLogger.getLogger(MosipPacketDTOProcessor.class);

    @Autowired
    CommonUtil commonUtil;

    @Autowired
    private QualityWriterFactory qualityWriterFactory;

    @Autowired
    private PacketCreator packetCreator;

    @Value("${mosip.packet.creater.source}")
    private String source;

    @Autowired
    private TrackerUtil trackerUtil;

    @Override
    public DataProcessorResponseDto process(DBImportRequest dbImportRequest, Object data, ResultSetter setter) throws Exception {
        DataProcessorResponseDto responseDto = new DataProcessorResponseDto();
        responseDto.setProcess(dbImportRequest.getProcess());
        responseDto.setResponses(new HashMap<>());

        String trackerColumn = dbImportRequest.getTrackerInfo().getTrackerColumn();
        Map<FieldCategory, HashMap<String, Object>> dataHashMap = (Map<FieldCategory, HashMap<String, Object>>) data;

        if ( dataHashMap != null) {
                String registrationId = null;

                if(!IS_ONLY_FOR_QUALITY_CHECK) {
                    if(applicationIdColumn != null && !applicationIdColumn.isEmpty()) {
                        if(dataHashMap.get(FieldCategory.DEMO).containsKey(applicationIdColumn)) {
                            registrationId = dataHashMap.get(FieldCategory.DEMO).get(applicationIdColumn).toString();
                        } else {
                            LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Application ID : " + applicationIdColumn + " not found in DataMap");
                            throw new Exception("Application ID : " + applicationIdColumn + " not found in DataMap");
                        }
                    } else {
                        registrationId = commonUtil.generateRegistrationId(ConfigUtil.getConfigUtil().getCenterId(), ConfigUtil.getConfigUtil().getMachineId());
                    }
                }
                trackerUtil.addTrackerLocalEntry(dataHashMap.get(FieldCategory.DEMO).get(dbImportRequest.getTrackerInfo().getTrackerColumn()).toString(), registrationId, TrackerStatus.STARTED, dbImportRequest.getProcess(), null, SESSION_KEY, getActivityName());

                Long startTime = System.nanoTime();
                HashMap<String, Object> demoDetails = dataHashMap.get(FieldCategory.DEMO);
                HashMap<String, Object> bioDetails = dataHashMap.get(FieldCategory.BIO);
                HashMap<String, Object> docDetails = dataHashMap.get(FieldCategory.DOC);
                String refId = registrationId == null ? demoDetails.get(trackerColumn).toString() : registrationId;
                responseDto.setRefId(demoDetails.get(trackerColumn).toString());
                responseDto.setTrackerRefId(refId);
                LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Thread - " + refId + " Process Started");

                try {
                    HashMap<String, String> csvMap = qualityWriterFactory.getDataMap();
                    HashMap<String, String> metaInfo = new HashMap<>();

                    PacketDto packetDto = new PacketDto();
                    packetDto.setProcess(dbImportRequest.getProcess());
                    packetDto.setSource(source);
                    packetDto.setSchemaVersion(String.valueOf(commonUtil.getLatestIdSchema().get("idVersion")));
                    packetDto.setAdditionalInfoReqId(null);
                    packetDto.setMetaInfo(null);
                    packetDto.setOfflineMode(false);
                    packetDto.setId(registrationId);

                    if (!IS_ONLY_FOR_QUALITY_CHECK && docDetails.size() > 0) {
                        packetDto.setDocuments(packetCreator.setDocuments(docDetails, dbImportRequest.getIgnoreIdSchemaFields(), metaInfo, demoDetails));
                    }
                    Long timeDifference = System.nanoTime()-startTime;
                    LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken for Completion of Document Process " + refId + " " + TimeUnit.MILLISECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));

                    if (!IS_ONLY_FOR_QUALITY_CHECK && demoDetails.size() > 0) {
                        packetDto.setFields(packetCreator.setDemographic(demoDetails, (bioDetails.size() > 0), dbImportRequest.getIgnoreIdSchemaFields()));
                    }

                    timeDifference = System.nanoTime()-startTime;
                    LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken for Completion of Demographic Process " + refId + " " + TimeUnit.MILLISECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));

                    if (bioDetails.size() > 0) {
                        LOGGER.debug("SESSION_ID", "DATA_PROCESSOR", "process()", "Reference Id : " + refId + " Biometrics found size is  : " + bioDetails.size());
                        packetDto.setBiometrics(packetCreator.setBiometrics(bioDetails, metaInfo, csvMap, refId, startTime));
                    }

                    timeDifference = System.nanoTime()-startTime;
                    LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken for Completion of Biometric Process " + refId + " " + TimeUnit.MILLISECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));

                    csvMap.put("reg_no", registrationId);
                    csvMap.put("ref_id", demoDetails.get(trackerColumn).toString());
                    qualityWriterFactory.writeQualityData(csvMap);

                    packetDto.setRefId(ConfigUtil.getConfigUtil().getCenterId() + "_" + ConfigUtil.getConfigUtil().getMachineId());
                    packetCreator.setMetaData(metaInfo, packetDto, dbImportRequest);
                    packetDto.setMetaInfo(metaInfo);
                    packetDto.setAudits(packetCreator.setAudits(packetDto.getId()));
                    timeDifference = System.nanoTime()-startTime;
                    LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken for Completion of Audit Log set Process " + refId + " " + TimeUnit.MILLISECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));

                    HashMap<String, Object> idSchema = commonUtil.getLatestIdSchema();
                    packetDto.setSchemaJson(idSchema.get("schemaJson").toString());
                    packetDto.setOfflineMode(true);

                    timeDifference = System.nanoTime()-startTime;
                    LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken for Fetching Latest ID Schema " + refId + " " + TimeUnit.MILLISECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));

                    responseDto.getResponses().put("demoDetails", demoDetails);
                    responseDto.getResponses().put("packetDto", packetDto);
                    return responseDto;
                } catch (Exception e) {
                    LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Exception : " + e.getMessage(), e);
                    ResultDto resultDto = new ResultDto();
                    resultDto.setRegNo(null);
                    resultDto.setRefId(demoDetails.get(trackerColumn).toString());
                    resultDto.setComments(e.getMessage());
                    resultDto.setStatus(TrackerStatus.FAILED);
                    setter.setResult(resultDto);
                    throw e;
                }
            }
            return null;

    }
}
