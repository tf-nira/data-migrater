package io.mosip.packet.extractor.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.commons.packet.constants.Biometric;
import io.mosip.commons.packet.constants.PacketManagerConstants;
import io.mosip.commons.packet.dto.Document;
import io.mosip.commons.packet.dto.packet.DeviceMetaInfo;
import io.mosip.commons.packet.dto.packet.DigitalId;
import io.mosip.commons.packet.dto.packet.DocumentType;
import io.mosip.commons.packet.dto.packet.PacketDto;
import io.mosip.kernel.biometrics.constant.BiometricType;
import io.mosip.kernel.biometrics.entities.BIR;
import io.mosip.kernel.biometrics.entities.BiometricRecord;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.packet.core.config.biosdk.BioSDKConfig;
import io.mosip.packet.core.constant.RegistrationConstants;
import io.mosip.packet.core.dto.biosdk.BioSDKRequestWrapper;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.dto.mockmds.BioMetricsDto;
import io.mosip.packet.core.dto.mockmds.CaptureRequestDeviceDetailDto;
import io.mosip.packet.core.dto.mockmds.CaptureRequestDto;
import io.mosip.packet.core.dto.mockmds.RCaptureResponseDataDTO;
import io.mosip.packet.core.dto.packet.BioData;
import io.mosip.packet.core.dto.packet.BiometricsDto;
import io.mosip.packet.core.dto.packet.metadata.BiometricsMetaInfoDto;
import io.mosip.packet.core.dto.packet.metadata.DocumentMetaInfoDTO;
import io.mosip.packet.core.dto.packet.type.IndividualBiometricType;
import io.mosip.packet.core.dto.packet.type.SimpleType;
import io.mosip.packet.core.exception.PlatformErrorMessages;
import io.mosip.packet.core.exception.ValidationFailedException;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.repository.BlocklistedWordsRepository;
import io.mosip.packet.core.service.DataRestClientService;
import io.mosip.packet.core.spi.BioSdkApiFactory;
import io.mosip.packet.core.util.BioSDKUtil;
import io.mosip.packet.core.util.CommonUtil;
import io.mosip.packet.core.util.mockmds.StringHelper;
import io.mosip.packet.manager.util.mock.sbi.devicehelper.MockDeviceUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static io.mosip.packet.core.constant.GlobalConfig.IS_ONLY_FOR_QUALITY_CHECK;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
public class PacketCreator {

    private static final Logger LOGGER = DataProcessLogger.getLogger(PacketCreator.class);

    @Autowired
    private DataRestClientService restApiClient;

    @Autowired
    private Environment env;

    @Autowired
    private BIRBuilder birBuilder;

    @Autowired
    private MockDeviceUtil mockDeviceUtil;

    @Autowired
    private CommonUtil commonUtil;

    @Value("${mosip.id.schema.version:0.1}")
    private String version;

    @Value("${mosip.primary.language}")
    private String primaryLamguage;

    @Value("${mosip.packet.creator.environment:Staging}")
    private String environment;

    @Value("${mosip.packet.creator.purpose:Registration}")
    private String purpose;

    @Value("${mosip.packet.creator.requested.score}")
    private String requestedScore;

    @Value("${mosip.bio.spec.version:0.9.5}")
    private String bioSpecVaersion;

    @Value("${mosip.registration.mdm.trust.domain.rcapture:DEVICE}")
    private String rCaptureTrustDomain;

    @Value("${mosip.packet.biosdk.quality.check.enabled:true}")
    private boolean biosdkCheckEnabled;

    @Value("${mosip.packet.creator.biometric.poor.quality.ranges:0-15}")
    private String poorBioQualityRange;

    @Value("${mosip.packet.creator.restrict.poor.biometric.creation:FACE}")
    private String restrictTypes;

    @Value("${mosip.packet.creator.enable.poor.biometric.to.exception:false}")
    private boolean poorBirCreation;

    @Autowired
    private MosipDeviceSpecificationHelper mosipDeviceSpecificationHelper;

    @Autowired
    private BlocklistedWordsRepository blocklistedWordsRepository;

    @Autowired
    private BioSDKConfig bioSDKConfig;

    private ObjectMapper mapper = new ObjectMapper();

    private Map<String, int[]> qualityRangeMap;

    @Value("${mosip.packet.creater.source}")
    private String source;

    private String TOOL_NAME = "DATAMIGRATOR";

    @Autowired
    private BioSDKUtil bioSDKUtil;

    @PostConstruct
    private void initializeEVariables() {
        String[] rangeArraay = poorBioQualityRange.split("-");
        qualityRangeMap = new HashMap<>();
        int[] intMap = new int[2];
        intMap[0] = Integer.parseInt(rangeArraay[0]);
        intMap[1] = Integer.parseInt(rangeArraay[1]);
        qualityRangeMap.put("POOR", intMap);
    }

    public HashMap<String, String> setDemographic(HashMap<String, Object> demoDetails, Boolean isBiometricPresent, List ignorableFields) throws Exception {
        HashMap<String, String> demoMap = new HashMap<>();
        HashMap<String, Object> idSchema = commonUtil.getLatestIdSchema();

        for(Object obj : (List)idSchema.get("schema")) {
            Map<String, Object> map = (Map<String, Object>) obj;
            String id = map.get("id").toString();
            String type = map.get("type").toString();
            Boolean required = (Boolean) map.get("required");
            if(required == null)
                required = false;

            if (id.equals("IDSchemaVersion")) {
                demoMap.put(id, version);
            } else if (type.equals("biometricsType")) {
                if (id.equals("individualBiometrics")) {
                    if (isBiometricPresent) {
                        IndividualBiometricType indiBiotype = new IndividualBiometricType();
                        indiBiotype.setFormat("cbeff");
                        indiBiotype.setVersion(1.0);
                        indiBiotype.setValue("individualBiometrics_bio_CBEFF");
                        demoMap.put("individualBiometrics", mapper.writeValueAsString(indiBiotype));
                    }
                }
            } else if (type.equals("documentType")) {
                if (demoDetails.containsKey(id) && demoDetails.get(id) != null)
                    demoMap.put(id, String.valueOf(demoDetails.get(id)));
            } else if (demoDetails.containsKey(id) && demoDetails.get(id) != null) {
                switch (type) {
                    case "simpleType":
                        List<SimpleType> valList = new ArrayList<>();
                        SimpleType simpleType = new SimpleType(primaryLamguage, demoDetails.get(id) == null ? "":demoDetails.get(id).toString());
                        valList.add(simpleType);
                        demoMap.put(id, mapper.writeValueAsString(valList));
                        break;

                    case "number":

                    case "string" :
                        demoMap.put(id, demoDetails.get(id) == null ? "" : String.valueOf(demoDetails.get(id)));
                        break;
                }
            } else if (required && !ignorableFields.contains(id)) {
                throw new Exception("Mandatory Field '" + id + "' value missing");
            }
        }
        return demoMap;
    }

    public HashMap<String, Document> setDocuments(HashMap<String, Object> docDetails, List ignorableFields, HashMap<String, String> metaInfoMap, HashMap<String, Object> demoDetails)
            throws Exception {

        HashMap<String, Document> docMap = new HashMap<>();
        HashMap<String, Object> idSchema = commonUtil.getLatestIdSchema();

        for(Object obj : (List)idSchema.get("schema")) {
            Map<String, Object> map = (Map<String, Object>) obj;
            String id = map.get("id").toString();
            String type = map.get("type").toString();
            Boolean required = (Boolean) map.get("required");
            String subType = (String) map.get("subType");

            if (type.equals("documentType")) {
                if (docDetails.containsKey(id) && docDetails.get(id) != null) {
                    Document document = mapper.readValue((String) docDetails.get(id), new TypeReference<Document>() {});
                    Document documentDto = new Document();
//                    documentDto.setOwner("Applicant");
                    documentDto.setDocument(document.getDocument());
//                    documentDto.setCategory(subType);
                    documentDto.setFormat(document.getFormat());
                    documentDto.setType(document.getType());
                    documentDto.setRefNumber(document.getRefNumber());
                    documentDto.setValue(id);
                    docMap.put(id,documentDto);

                    DocumentType documentType = new DocumentType(id, document.getType(), document.getFormat(), document.getRefNumber());
                    demoDetails.put(id, mapper.writeValueAsString(documentType));
                }  else if (required && !ignorableFields.contains(id)) {
                    throw new Exception("Mandatory Field '" + id + "' value missing");
                }

            }
        }

        List<DocumentMetaInfoDTO> documentMetaInfoDTOs = new LinkedList<>();
        for (String fieldName : docMap.keySet()) {
            Document document = docMap.get(fieldName);
            DocumentMetaInfoDTO documentMetaInfoDTO = new DocumentMetaInfoDTO();
//            documentMetaInfoDTO.setDocumentCategory(document.getCategory());
            documentMetaInfoDTO.setDocumentName(document.getValue());
//            documentMetaInfoDTO.setDocumentOwner(document.getOwner());
            documentMetaInfoDTO.setDocumentType(document.getType());
            documentMetaInfoDTO.setRefNumber(document.getRefNumber());

            documentMetaInfoDTOs.add(documentMetaInfoDTO);
        }

        metaInfoMap.put("documents", mapper.writeValueAsString(documentMetaInfoDTOs));
        return docMap;
    }

    public LinkedHashMap<String, BiometricRecord> setBiometrics(HashMap<String, Object> bioDetails, HashMap<String, String> metaInfoMap, HashMap<String, String> csvMap, String trackerColumn) throws Exception {
        HashMap<String, Object> idSchema = commonUtil.getLatestIdSchema();
        LOGGER.debug("Adding Biometrics to packet manager started..");
        HashMap<String, List<BIR>> capturedBiometrics = new HashMap<>();
        Map<String, Map<String, Object>> capturedMetaInfo = new HashMap<>();
        Map<String, Map<String, Object>> exceptionMetaInfo = new HashMap<>();
        Map<String, DeviceMetaInfo> capturedRegisteredDevices = new HashMap<>();

        LinkedHashMap<String, BiometricRecord> biometricsMap = new LinkedHashMap<>();

        for(Object obj : (List)idSchema.get("schema")) {
            Map<String, Object> map = (Map<String, Object>) obj;
            String id = map.get("id").toString();
            String type = map.get("type").toString();
            Boolean required = (Boolean) map.get("required");
            String subtype = map.get("subType") == null ? null : map.get("subType").toString();

            if (type.equals("biometricsType")) {
                List<String> bioAttributes = new ArrayList<String>();
                bioAttributes.addAll((List<String>) map.get("bioAttributes"));
                Integer attributeCount = bioAttributes.size();
                bioAttributes.add("unknown");

                 for (Map.Entry<String, Object> entry : bioDetails.entrySet()) {
                    Long startTime = System.nanoTime();
                    String[] keyEntries = entry.getKey().split("_");
                    String fieldId = keyEntries[0];
                    String bioAttribute = keyEntries.length > 1 ? keyEntries[1] : null;

                    if(fieldId.equals(id) && bioAttributes.contains(bioAttribute)) {
                        if(entry.getValue() != null && !entry.getValue().toString().isEmpty()) {
                            BioData bioData = (BioData) entry.getValue();
                            if(bioData.getBioData() != null) {
                                bioAttributes.remove(bioAttribute);
                                String bioQualityScore = !bioData.getQualityScore().isEmpty() ? bioData.getQualityScore() : null;
                                String bioType = Biometric.getSingleTypeByAttribute(bioAttribute).value();
                                CaptureRequestDto captureRequestDto = new CaptureRequestDto();
                                CaptureRequestDeviceDetailDto captureRequestDeviceDetailDto = new CaptureRequestDeviceDetailDto();
                                captureRequestDeviceDetailDto.setType(bioType);
                                captureRequestDeviceDetailDto.setBioSubType(bioAttribute);
                                captureRequestDeviceDetailDto.setRequestedScore(Integer.parseInt(requestedScore));

                                captureRequestDto.setEnv(environment);
                                captureRequestDto.setPurpose(purpose);
                                captureRequestDto.setSpecVersion(bioSpecVaersion);
                                captureRequestDto.setTransactionId(UUID.randomUUID().toString());
                                captureRequestDto.setBio(captureRequestDeviceDetailDto);

                                BioMetricsDto bioMetricsDto = mockDeviceUtil.getBiometricData(bioType, captureRequestDto, StringHelper.base64UrlEncode((byte[]) bioData.getBioData()), "en", "0");

                                String payLoad = mosipDeviceSpecificationHelper.getPayLoad(bioMetricsDto.getData());
                                String signature = mosipDeviceSpecificationHelper.getSignature(bioMetricsDto.getData());

                                String decodedPayLoad = new String(CryptoUtil.decodeURLSafeBase64(payLoad));
                                RCaptureResponseDataDTO dataDTO = mapper.readValue(decodedPayLoad, RCaptureResponseDataDTO.class);

                                if(!capturedRegisteredDevices.containsKey(dataDTO.getBioType())) {
                                    String decodeddigitalId = mosipDeviceSpecificationHelper.getDigitalId(dataDTO.getDigitalId());
                                    DeviceMetaInfo deviceMetaInfo = new DeviceMetaInfo();
                                    deviceMetaInfo.setDeviceCode(dataDTO.getDeviceCode());
                                    deviceMetaInfo.setDeviceServiceVersion(dataDTO.getDeviceServiceVersion());
                                    DigitalId digitalId = mapper.readValue(Base64.getDecoder().decode(decodeddigitalId), DigitalId.class);
                                    deviceMetaInfo.setDigitalId(digitalId);
                                    capturedRegisteredDevices.put(dataDTO.getBioType(), deviceMetaInfo);
                                }

                                BiometricsDto biometricDTO = new BiometricsDto(bioAttribute, dataDTO.getDecodedBioValue(),
                                        Double.parseDouble(dataDTO.getQualityScore()== null ? "0" : dataDTO.getQualityScore()));
                                biometricDTO.setPayLoad(decodedPayLoad);
                                biometricDTO.setSignature(signature);
                                biometricDTO.setSpecVersion(bioMetricsDto.getSpecVersion());
                                biometricDTO.setCaptured(true);
                                biometricDTO.setAttributeISO((byte[]) bioData.getBioData());
                                BIR bir = birBuilder.buildBIR(biometricDTO);
                                Long timeDifference = System.nanoTime()-startTime;
                                LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Completed BIR Builder for " + trackerColumn + " - " + entry.getKey() + " " + TimeUnit.SECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));

                                if (bioQualityScore==null) {
                                    if(biosdkCheckEnabled) {
                                        BiometricType biometricType = Biometric.getSingleTypeByAttribute(bioAttribute);
                                        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Fetch Biometric Type For BIOSDK Quality Calculation" + trackerColumn + " - " + entry.getKey() + " " + TimeUnit.SECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS));

                                        BioSDKRequestWrapper requestWrapper = new BioSDKRequestWrapper();
                                        requestWrapper.setSegments(new ArrayList<>());
                                        requestWrapper.getSegments().add(bir);
                                        requestWrapper.setBiometricType(biometricType.toString());
                                        requestWrapper.setFormat(bioData.getFormat().toString());
                                        requestWrapper.setInputObject(csvMap);
                                        requestWrapper.setIsOnlyForQualityCheck(IS_ONLY_FOR_QUALITY_CHECK);

                                        try {
                                            Double score = Double.parseDouble(bioSDKUtil.calculateQualityScore(requestWrapper, entry.getKey(), trackerColumn, startTime));
                                            if(!IS_ONLY_FOR_QUALITY_CHECK) {
                                                int[] qualityArray = qualityRangeMap.get("POOR");
                                                if(poorBirCreation && score >= qualityArray[0] && score <= qualityArray[1]) {
                                                    if(!restrictTypes.contains(biometricType.value().toUpperCase())) {
                                                        biometricDTO.setCaptured(false);
                                                        biometricDTO.setSignature(null);
                                                        biometricDTO.setAttributeISO(null);
                                                        biometricDTO.setPayLoad(null);
                                                        biometricDTO.setSdkScore(score);
                                                        biometricDTO.setRemarks("Marked as Exception by Migrator Tool due to Poor Quality Score");
                                                        bir = birBuilder.buildBIR(biometricDTO);

                                                        if(csvMap.containsKey("EXCEPTION_MARKED_BY_TOOL")) {
                                                            csvMap.put("EXCEPTION_MARKED_BY_TOOL", csvMap.get("EXCEPTION_MARKED_BY_TOOL") + ",\n"+entry.getKey());
                                                        } else {
                                                            csvMap.put("EXCEPTION_MARKED_BY_TOOL", entry.getKey());
                                                        }
                                                    } else
                                                        throw new ValidationFailedException(PlatformErrorMessages.MGR_PKT_CRT_IGNORE_EXCEPTION.getCode(), String.format(PlatformErrorMessages.MGR_PKT_CRT_IGNORE_EXCEPTION.getMessage(), biometricType.value().toUpperCase()));
                                                } else
                                                    bir.getBdbInfo().getQuality().setScore(score.longValue());
                                            }

                                            timeDifference = System.nanoTime()-startTime;
                                            LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "After Calculation of Quality from BIOSDK " + trackerColumn + " - " + entry.getKey() + " " + TimeUnit.SECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));
                                        } catch (ValidationFailedException e) {
                                            throw new Exception(trackerColumn + " Error : " + biometricType.toString() + ", " + bioAttribute + " Error Message :" + e.getLocalizedMessage());
                                        } catch (Exception e) {
                                            e.printStackTrace();
                                            bir.getBdbInfo().getQuality().setScore(null);
                                //            throw new Exception(trackerColumn + " Error : " + biometricType.toString() + ", " + bioAttribute + " Error Message :" + e.getLocalizedMessage());
                                        }
                                    } else {
                                        bir.getBdbInfo().getQuality().setScore(0L);
                                    }

                                } else {
                                    bir.getBdbInfo().getQuality().setScore(Long.parseLong(bioQualityScore));
                                }

                                if (!capturedBiometrics.containsKey(fieldId)) {
                                    capturedBiometrics.put(fieldId, new ArrayList<>());
                                }
                                capturedBiometrics.get(fieldId).add(bir);
                                if (!capturedMetaInfo.containsKey(fieldId)) {
                                    capturedMetaInfo.put(fieldId, new HashMap<>());
                                }
                                capturedMetaInfo.get(fieldId).put(bioAttribute, new BiometricsMetaInfoDto(1, false, bir.getBdbInfo().getIndex()));
                            } else {
                                BiometricsDto biometricDTO = new BiometricsDto(bioAttribute, null, Double.parseDouble("0"));
                                biometricDTO.setSpecVersion(bioSpecVaersion);
                                biometricDTO.setCaptured(false);
                                biometricDTO.setAttributeISO(null);
                                biometricDTO.setNotAvailable(false);
                                BIR bir = birBuilder.buildBIR(biometricDTO);
                                if (!capturedBiometrics.containsKey(id)) {
                                    capturedBiometrics.put(id, new ArrayList<>());
                                }
                                capturedBiometrics.get(id).add(bir);
                                if (!capturedMetaInfo.containsKey(id)) {
                                    capturedMetaInfo.put(id, new HashMap<>());
                                }
                                capturedMetaInfo.get(id).put(bioAttribute, new BiometricsMetaInfoDto(1, false, bir.getBdbInfo().getIndex()));
                                bioAttributes.remove(bioAttribute);
                            }
                       } else {
                            BiometricsDto biometricDTO = new BiometricsDto(bioAttribute, null, Double.parseDouble("0"));
                            biometricDTO.setSpecVersion(bioSpecVaersion);
                            biometricDTO.setCaptured(false);
                            biometricDTO.setAttributeISO(null);
                            biometricDTO.setNotAvailable(false);
                            BIR bir = birBuilder.buildBIR(biometricDTO);
                            if (!capturedBiometrics.containsKey(id)) {
                                capturedBiometrics.put(id, new ArrayList<>());
                            }
                            capturedBiometrics.get(id).add(bir);
                            if (!capturedMetaInfo.containsKey(id)) {
                                capturedMetaInfo.put(id, new HashMap<>());
                            }
                            capturedMetaInfo.get(id).put(bioAttribute, new BiometricsMetaInfoDto(1, false, bir.getBdbInfo().getIndex()));
                            bioAttributes.remove(bioAttribute);
                        }
                    }
                }
                bioAttributes.remove("unknown");

                if (attributeCount.equals(bioAttributes.size()))
                    bioAttributes.clear();

                for (String bioAttribute : bioAttributes) {
                    BiometricsDto biometricDTO = new BiometricsDto(bioAttribute, null, Double.parseDouble("0"));
                    biometricDTO.setSpecVersion(bioSpecVaersion);
                    biometricDTO.setCaptured(false);
                    biometricDTO.setAttributeISO(null);
                    biometricDTO.setNotAvailable(true);
                    BIR bir = birBuilder.buildBIR(biometricDTO);
                    if (!capturedBiometrics.containsKey(id)) {
                        capturedBiometrics.put(id, new ArrayList<>());
                    }
                    capturedBiometrics.get(id).add(bir);
                    if (!capturedMetaInfo.containsKey(id)) {
                        capturedMetaInfo.put(id, new HashMap<>());
                    }
                    capturedMetaInfo.get(id).put(bioAttribute, new BiometricsMetaInfoDto(1, false, bir.getBdbInfo().getIndex()));
                }
            }

        }

  /*      for(String key : registrationDTO.getBiometricExceptions().keySet()) {
            String fieldId = key.split("_")[0];
            String bioAttribute = key.split("_")[1];
            BIR bir = birBuilder.buildBIR(new BiometricsDto(bioAttribute, null, 0));
            capturedBiometrics.getOrDefault(fieldId, new ArrayList<>()).add(bir);
            exceptionMetaInfo.getOrDefault(fieldId, new HashMap<>()).put(bioAttribute,
                    registrationDTO.getBiometricExceptions().get(key));
        } */

        capturedBiometrics.keySet().forEach(fieldId -> {
            BiometricRecord biometricRecord = new BiometricRecord();
            biometricRecord.setOthers(new HashMap<>());
//            biometricRecord.getOthers().put(OtherKey.CONFIGURED, String.join(",",
//                    registrationDTO.CONFIGURED_BIOATTRIBUTES.getOrDefault(fieldId, Collections.EMPTY_LIST)));
            biometricRecord.setSegments(capturedBiometrics.get(fieldId));
            LOGGER.debug("Adding biometric to packet manager for field : {}", fieldId);
            biometricsMap.put(fieldId, biometricRecord);
        });

        metaInfoMap.put("biometrics", mapper.writeValueAsString(capturedMetaInfo));
        metaInfoMap.put("exceptionBiometrics", mapper.writeValueAsString(exceptionMetaInfo));
        metaInfoMap.put("capturedRegisteredDevices", mapper.writeValueAsString(capturedRegisteredDevices.values()));

        return biometricsMap;
    }

    public List<Map<String, String>> setAudits(String regId) {
        LocalDateTime responsetime = LocalDateTime.now(ZoneId.of("UTC"));
        String timeStamp = responsetime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"));

        List<Map<String, String>> auditList = new LinkedList<>();

        Map<String, String> auditMap = new LinkedHashMap<>();
        auditMap.put("uuid", UUID.randomUUID().toString());
        auditMap.put("createdAt", timeStamp);
        auditMap.put("eventId", "DATA_MIGRATOR_FROM_OTHER_DOMAIN");
        auditMap.put("eventName", "ADD");
        auditMap.put("eventType", "REGISTRATION");
        auditMap.put("hostName", "DATA_MIGRATOR");
        auditMap.put("hostIp", null);
        auditMap.put("applicationId", "REG");
        auditMap.put("applicationName", "REGISTRATION");
        auditMap.put("sessionUserId", null);
        auditMap.put("sessionUserName", null);
        auditMap.put("id", regId);
        auditMap.put("idType", "REGISTRATION_ID");
        auditMap.put("createdBy", "MOSIP");
        auditMap.put("moduleName", "Registration initialization");
        auditMap.put("moduleId", null);
        auditMap.put("description", "Registration Via Data Migrator");
        auditMap.put("actionTimeStamp", timeStamp);
        auditList.add(auditMap);
        return auditList;
    }

    public void setMetaData(Map<String, String> metaInfoMap, PacketDto packetDto, DBImportRequest dbImportRequest) throws JsonProcessingException {
        Map<String, String> metaData = new LinkedHashMap<>();
        metaData.put(PacketManagerConstants.REGISTRATIONID, packetDto.getId());
        metaData.put(RegistrationConstants.PACKET_APPLICATION_ID, packetDto.getId());
        metaData.put(PacketManagerConstants.META_CREATION_DATE, LocalDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")));
        metaData.put(PacketManagerConstants.META_CLIENT_VERSION, ConfigUtil.getConfigUtil().getRegClientVersion());
        metaData.put(PacketManagerConstants.META_REGISTRATION_TYPE, dbImportRequest.getProcess());
        metaData.put(PacketManagerConstants.META_PRE_REGISTRATION_ID, null);
        metaData.put(PacketManagerConstants.META_MACHINE_ID, ConfigUtil.getConfigUtil().getMachineId());
        metaData.put(PacketManagerConstants.META_CENTER_ID, ConfigUtil.getConfigUtil().getCenterId());
        metaData.put(PacketManagerConstants.META_KEYINDEX, ConfigUtil.getConfigUtil().getKeyIndex());
        metaData.put(PacketManagerConstants.META_DONGLE_ID, ConfigUtil.getConfigUtil().getMachineSerialNum());
        metaData.put("langCodes", String.join(RegistrationConstants.COMMA, ConfigUtil.getConfigUtil().getSelectedLanguages()));
        metaData.put(PacketManagerConstants.META_APPLICANT_CONSENT, null);
        metaData.put("packetSource", TOOL_NAME);

        metaInfoMap.put("metaData", mapper.writeValueAsString(getLabelValueDTOListString(metaData)));
        metaInfoMap.put("blockListedWords", mapper.writeValueAsString(  blocklistedWordsRepository.findAllActiveBlockListedWords()));
        metaInfoMap.put("capturedNonRegisteredDevices", mapper.writeValueAsString(new ArrayList<>()));
        metaInfoMap.put("printingName", mapper.writeValueAsString(new ArrayList<>()));
        setOperationsData(metaInfoMap);
    }

    private void setOperationsData(Map<String, String> metaInfoMap) throws JsonProcessingException {

        Map<String, String> operationsDataMap = new LinkedHashMap<>();
        operationsDataMap.put(PacketManagerConstants.META_OFFICER_ID, env.getProperty("mosip.registraion-client.officerid"));
        operationsDataMap.put(PacketManagerConstants.META_OFFICER_BIOMETRIC_FILE, null);
        operationsDataMap.put(PacketManagerConstants.META_SUPERVISOR_ID,null);
        operationsDataMap.put(PacketManagerConstants.META_SUPERVISOR_BIOMETRIC_FILE,null);
        operationsDataMap.put(PacketManagerConstants.META_SUPERVISOR_PWD,"false");
        operationsDataMap.put(PacketManagerConstants.META_OFFICER_PWD,"true");
        operationsDataMap.put(PacketManagerConstants.META_SUPERVISOR_PIN, null);
        operationsDataMap.put(PacketManagerConstants.META_OFFICER_PIN, null);
        operationsDataMap.put(PacketManagerConstants.META_SUPERVISOR_OTP,"false");
        operationsDataMap.put(PacketManagerConstants.META_OFFICER_OTP,"false");

        metaInfoMap.put(PacketManagerConstants.META_INFO_OPERATIONS_DATA,
                mapper.writeValueAsString(getLabelValueDTOListString(operationsDataMap)));

    }

    private List<Map<String, String>> getLabelValueDTOListString(Map<String, String> operationsDataMap) {

        List<Map<String, String>> labelValueMap = new LinkedList<>();

        for (Map.Entry<String, String> fieldName : operationsDataMap.entrySet()) {

            Map<String, String> map = new HashMap<>();

            map.put("label", fieldName.getKey());
            map.put("value", fieldName.getValue());

            labelValueMap.add(map);
        }

        return labelValueMap;
    }
}
