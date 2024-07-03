package io.mosip.packet.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.mosip.commons.packet.dto.packet.PacketDto;
import io.mosip.kernel.biometrics.entities.BIR;
import io.mosip.kernel.biometrics.spi.CbeffUtil;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.packet.core.constant.tracker.TrackerStatus;
import io.mosip.packet.core.dto.DataPostProcessorResponseDto;
import io.mosip.packet.core.dto.DataProcessorResponseDto;
import io.mosip.packet.core.dto.ResponseWrapper;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.service.thread.ResultDto;
import io.mosip.packet.core.service.thread.ResultSetter;
import io.mosip.packet.core.spi.datapostprocessor.DataPostProcessor;
import io.mosip.packet.core.util.TrackerUtil;
import io.mosip.packet.data.dto.Documents;
import io.mosip.packet.data.dto.IdRequestDto;
import io.mosip.packet.data.dto.RequestDto;
import io.mosip.packet.data.service.ImportIdentityService;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
public class IdrepoUploader implements DataPostProcessor {

    private static final Logger logger = DataProcessLogger.getLogger(IdrepoUploader.class);
    private static final String VERSION = "v1";
    //private static final SimpleDateFormat simpleDateParser = new SimpleDateFormat("yyyy-MM-dd");
    //private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy/MM/dd");

    @Autowired
    ObjectMapper mapper;

    @Autowired
    private CbeffUtil cbeffUtil;

    @Value("${mosip.kernel.xsdfile}")
    private String cbeffXsd;

    @Autowired
    private ImportIdentityService importIdentityService;

    @Autowired
    private TrackerUtil trackerUtil;

    private byte[] xsd;

    @Override
    public DataPostProcessorResponseDto postProcess(DataProcessorResponseDto processObject, ResultSetter setter, Long startTime) throws Exception {
        DataPostProcessorResponseDto responseDto = new DataPostProcessorResponseDto();
        responseDto.setProcess(processObject.getProcess());
        responseDto.setRefId(processObject.getRefId());
        responseDto.setTrackerRefId(processObject.getTrackerRefId());
        responseDto.setResponses(new HashMap<>());

        PacketDto packetDto = (PacketDto) processObject.getResponses().get("packetDto");
        HashMap<String, Object> demoDetails = (HashMap<String, Object>) processObject.getResponses().get("demoDetails");
        String trackerRefId = processObject.getTrackerRefId();

        logger.info("Entering Idrepo identity Uploader, RID:{},NRCID:{} ", packetDto.getId(),
                packetDto.getFields().get("nrcId"));
        Long timeDifference = System.nanoTime()-startTime;
        logger.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken to enter the id repo file. " + trackerRefId + " " + TimeUnit.SECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));

        Map<String, Object> identity = new HashMap<>();
        Map<String, String> packetFields = (Map<String, String>) packetDto.getFields();
        for (Map.Entry<String, String> entry : packetFields.entrySet()) {
            if (entry.getKey().equalsIgnoreCase("dateOfBirth")) {
                //Date dob = simpleDateParser.parse(entry.getValue());
                identity.put(entry.getKey(), entry.getValue().replaceAll("-","/"));
            } else if (entry.getKey().equalsIgnoreCase("IDSchemaVersion")) {
                identity.put(entry.getKey(), NumberUtils.createDouble(entry.getValue()));
            } else if (entry.getKey().equalsIgnoreCase("phoneNumber")) {
                identity.put(entry.getKey(), entry.getValue().replaceAll("[\\s+?�^]*", ""));
            } else if (entry.getKey().equalsIgnoreCase("selectedHandles")
            || entry.getKey().equalsIgnoreCase("nrcId")
                    || entry.getKey().equalsIgnoreCase("UIN") || entry.getKey().equalsIgnoreCase("registrationId")) {
                identity.put(entry.getKey(), entry.getValue());
            } else {
                identity.put(entry.getKey(), mapper.readValue(entry.getValue(), Object.class));
            }
        }

        IdRequestDto idRequestDTO = new IdRequestDto();
// Setting the identity JSON object to the requestDto
        RequestDto requestDto = new RequestDto();
        requestDto.setRegistrationId(packetDto.getId());
        requestDto.setIdentity(identity);

        //logger.info("Processor: RequestDto ( {} )", requestDto);
        requestDto.setDocuments(getBiometricsAndDocuments(packetDto));
        // Setting the requestDto to the idRequestDto
        idRequestDTO.setId("mosip.id.create");
        idRequestDTO.setVersion(VERSION);
        idRequestDTO.setRequesttime(DateUtils.getUTCTimeFromDate(new Date()));
        idRequestDTO.setRequest(requestDto);
        ResponseWrapper response = importIdentityService.importIdentity(idRequestDTO, demoDetails);
        if (response != null && response.getResponse() != null) {
            logger.info("Import identity success, response: {}", response.getResponse());
            responseDto.getResponses().put("message", "Import identity success, response: " + response.getResponse());
            trackerStatusUpdate(processObject.getRefId(), packetDto, setter, TrackerStatus.PROCESSED,  (new Gson()).toJson(responseDto));
        } else if (response != null && response.getErrors() != null) {
            logger.error("Error response received: {}", response.getErrors());
            responseDto.getResponses().put("message", "Error response received: "+ response.getErrors());
            trackerStatusUpdate(processObject.getRefId(), packetDto, setter, TrackerStatus.FAILED, (new Gson()).toJson(responseDto));
        }
        timeDifference = System.nanoTime()-startTime;
        logger.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken to exit the id repo file. " + trackerRefId + " " + TimeUnit.SECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));

        return responseDto;
    }

    private void trackerStatusUpdate(String refId, PacketDto packetDto, ResultSetter setter, TrackerStatus status, String comment) {
        ResultDto resultDto = new ResultDto();
        resultDto.setRegNo(packetDto.getId());
        resultDto.setRefId(refId);
        resultDto.setStatus(status);
        resultDto.setComments(comment);
        setter.setResult(resultDto);
    }

    private List<Documents> getBiometricsAndDocuments(PacketDto packetDto) {
        List<Documents> documents = new ArrayList<>();
        if (packetDto.getBiometrics() != null) {
            List<BIR> birList = packetDto.getBiometrics().get("individualBiometrics").getSegments();
            try {
                byte[] bdbBytes = cbeffUtil.createXML(birList);
                String encodedCbeffFile = CryptoUtil.encodeToURLSafeBase64(bdbBytes);
                documents.add(new Documents("individualBiometrics", encodedCbeffFile));
            } catch (Exception e) {
                logger.error("Error while creating cbeff file.", e);
            }
        }
        if (packetDto.getDocuments() != null) {
            if (packetDto.getFields().get("proofOfIdentity") != null) {
                String encodedDocument = CryptoUtil.encodeToURLSafeBase64(packetDto.getDocuments().get("proofOfIdentity").getDocument());
                documents.add(new Documents("proofOfIdentity", encodedDocument));
            }
        }
        return documents;
    }


    @PostConstruct
    public void loadXSD() throws IOException {
        InputStream inputStream = null;
        try {
            inputStream = new ClassPathResource(cbeffXsd).getInputStream();
            this.xsd = IOUtils.toByteArray(inputStream);
        } catch (Throwable var5) {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (Throwable var4) {
                    var5.addSuppressed(var4);
                }
            }
        }
        if (inputStream != null) {
            inputStream.close();
        }

    }
}
