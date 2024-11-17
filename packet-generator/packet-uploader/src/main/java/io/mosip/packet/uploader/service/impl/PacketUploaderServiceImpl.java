package io.mosip.packet.uploader.service.impl;

import com.google.gson.Gson;
import io.mosip.commons.packet.spi.IPacketCryptoService;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.packetuploader.exception.ConnectionException;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.core.util.FileUtils;
import io.mosip.kernel.core.util.HMACUtils2;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.kernel.keymanagerservice.exception.KeymanagerServiceException;
import io.mosip.packet.core.constant.ApiName;
import io.mosip.packet.core.constant.RegistrationConstants;
import io.mosip.packet.core.dto.upload.PacketUploadDTO;
import io.mosip.packet.core.dto.upload.PacketUploadResponseDTO;
import io.mosip.packet.core.dto.upload.RegistrationPacketSyncDTO;
import io.mosip.packet.core.dto.ResponseWrapper;
import io.mosip.packet.core.dto.upload.SyncRegistrationDTO;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.service.DataRestClientService;
import io.mosip.packet.core.util.regclient.ServiceDelegateUtil;
import io.mosip.packet.uploader.service.PacketUploaderService;
import lombok.NonNull;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static io.mosip.kernel.core.util.JsonUtils.javaObjectToJsonString;
import static java.lang.Integer.parseInt;
import static java.lang.Long.parseLong;
import static io.mosip.packet.core.constant.RegistrationConstants.*;

@Service
public class PacketUploaderServiceImpl  implements PacketUploaderService {

    private static final Logger LOGGER = DataProcessLogger.getLogger(PacketUploaderServiceImpl.class);

        @Autowired
        private ServiceDelegateUtil restApiClient;

        @Value("${mosip.registration.retry.delay.packet.upload:1000}")
        private String MOSIP_RETRY_DELAY;

        @Value("${mosip.registration.retry.maxattempts.packet.upload:2}")
        private String MOSIP_RETRY_ATTEMPT;

        @Autowired
        private Environment environment;

        @Autowired
        @Qualifier("OfflinePacketCryptoServiceImpl")
        private IPacketCryptoService offlinePacketCryptoServiceImpl;

        @Value("${packet.manager.account.name}")
        private String packetUploadPath;

        private RetryTemplate retryTemplate;

        private String centerId;

        private String machineId;

    @PostConstruct
    public void init() {
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(parseLong(MOSIP_RETRY_DELAY));

        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(parseInt(MOSIP_RETRY_ATTEMPT));

        retryTemplate = new RetryTemplateBuilder()
                .retryOn(ConnectionException.class)
                .customPolicy(retryPolicy)
                .customBackoff(backOffPolicy)
                .build();
    }

    @Override
    public void syncPacket(@NonNull List<PacketUploadDTO> packets, String centerId, String machineId, HashMap<String, PacketUploadResponseDTO> response) throws Exception {
        try {
            Long startTime = System.nanoTime();
            this.centerId = centerId;
            this.machineId = machineId;
            String trackerRefId = packets.stream().map(p -> p.getPacketId()).collect(Collectors.toSet()).toString();
            LOGGER.debug("SESSION_ID", "PACKET_SYNC", "syncPacket()", "Time Taken for syncPacket Start. Reference ID : " + trackerRefId + " (" + TimeUnit.MILLISECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS) + " ms)");
            restApiClient.setCenterMachineId(centerId, machineId);
            Object obj = syncRIDToServerWithRetryWrapper(packets, trackerRefId, startTime);
            LOGGER.debug("SESSION_ID", "PACKET_SYNC", "syncPacket()", "Time Taken for syncPacket Complete. Reference ID : " + trackerRefId + " (" + TimeUnit.MILLISECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS) + " ms)");
            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Packet Sync API Response" + (new Gson()).toJson(obj));
        } catch (JsonProcessingException | KeymanagerServiceException e) {
            e.printStackTrace();
        }
    }

    private Object syncRIDToServerWithRetryWrapper(List<PacketUploadDTO> packets, String trackerRefId, Long startTime) throws Exception {
        RetryCallback<Boolean, Exception> retryCallback = new RetryCallback<Boolean, Exception>() {
            @SneakyThrows
            @Override
            public Boolean doWithRetry(RetryContext retryContext) throws Exception {
                syncRIDToServer(packets, startTime, trackerRefId);
                LOGGER.debug("SESSION_ID", "PACKET_SYNC", "syncRIDToServerWithRetryWrapper()", "Time Taken to complete sync. Reference ID : " + trackerRefId + " (" + TimeUnit.MILLISECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS) + " ms)");
                return true;
            }
        };
        return retryTemplate.execute(retryCallback);
    }

        private synchronized void syncRIDToServer(List<PacketUploadDTO> packets, Long startTime, String trackerRefId) throws Exception {

        List<SyncRegistrationDTO> syncDtoList = getPacketSyncDtoList(packets);
            LOGGER.debug("SESSION_ID", "PACKET_SYNC", "syncRIDToServer()", "Time Taken for getPacketSyncDtoList() Reference ID : " + trackerRefId + " (" + TimeUnit.MILLISECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS) + " ms)");
        List<SyncRegistrationDTO> syncDtoWithPacketId = syncDtoList.stream().filter(dto -> !dto.getRegistrationId().equals(dto.getPacketId())).collect(Collectors.toList());

        if (syncDtoList != null && !syncDtoList.isEmpty()) {
            try {
                syncRID(syncDtoWithPacketId, true, startTime, trackerRefId);
            } catch (Exception e) {
                throw e;
            }
        }
    }

        private List<SyncRegistrationDTO> getPacketSyncDtoList(@NonNull List<PacketUploadDTO> packets) throws IOException, NoSuchAlgorithmException
        {
            List<SyncRegistrationDTO> syncDtoList = new ArrayList<>();
            for (PacketUploadDTO packet : packets) {

                SyncRegistrationDTO syncDto = new SyncRegistrationDTO();
                syncDto.setRegistrationId(packet.getRegistrationId());
                syncDto.setRegistrationType(packet.getRegistrationType());
                syncDto.setPacketId(packet.getPacketId());
                syncDto.setSupervisorStatus(RegistrationConstants.CLIENT_STATUS_APPROVED);
                syncDto.setSupervisorComment("");
                syncDtoList.add(syncDto);
                syncDto.setLangCode(packet.getLangCode());
                syncDto.setName(packet.getName());
                syncDto.setPhone(packet.getPhone());
                syncDto.setEmail(packet.getEmail());

                try (FileInputStream fis = new FileInputStream(FileUtils.getFile(packet.getPacketPath() +
                        RegistrationConstants.SLASH + packet.getPacketId() + RegistrationConstants.ZIP_FILE_EXTENSION))) {
                    byte[] byteArray = new byte[(int) fis.available()];
                    fis.read(byteArray);
                    syncDto.setPacketHashValue(HMACUtils2.digestAsPlainText(byteArray));
                    syncDto.setPacketSize(BigInteger.valueOf(byteArray.length));
                } catch (IOException | NoSuchAlgorithmException ioException) {
                    throw ioException;
                }
            }
            return syncDtoList;
        }

        private void syncRID(List<SyncRegistrationDTO> syncDtoList, boolean packetIdExists, Long startTime, String trackerRefId) throws Exception {
        if (!syncDtoList.isEmpty()) {
            RegistrationPacketSyncDTO registrationPacketSyncDTO = new RegistrationPacketSyncDTO();
            registrationPacketSyncDTO
                    .setRequesttime(DateUtils.formatToISOString(DateUtils.getUTCCurrentDateTime()));
            registrationPacketSyncDTO.setSyncRegistrationDTOs(syncDtoList);
            registrationPacketSyncDTO.setId(RegistrationConstants.PACKET_SYNC_STATUS_ID);
            registrationPacketSyncDTO.setVersion(RegistrationConstants.PACKET_SYNC_VERSION);

            String refId = String.valueOf(centerId)
                    .concat(RegistrationConstants.UNDER_SCORE)
                    .concat(String.valueOf(machineId));

            LOGGER.debug("SESSION_ID", "PACKET_SYNC", "syncRID()", "Time Taken for before encryption. Reference Id : " + trackerRefId + " (" + TimeUnit.MILLISECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS) + " ms)");
            String encodedString = CryptoUtil.encodeToURLSafeBase64(offlinePacketCryptoServiceImpl.encrypt(refId, javaObjectToJsonString(registrationPacketSyncDTO).getBytes()));
            LOGGER.debug("SESSION_ID", "PACKET_SYNC", "syncRID()", "Time Taken for after encryption. Reference Id : " + trackerRefId + " (" + TimeUnit.MILLISECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS) + " ms)");
            syncPacketsToServer(encodedString, "User", packetIdExists, trackerRefId, startTime);

        }
    }

        private void syncPacketsToServer(@NonNull String encodedString, @NonNull String triggerPoint, boolean packetIdExists, String trackerRefId, Long startTime)
            throws Exception {
        try {
            HashMap<String, Object> response = (HashMap<String, Object>) restApiClient
                    .post(packetIdExists ? RegistrationConstants.PACKET_SYNC_V2 : RegistrationConstants.PACKET_SYNC, javaObjectToJsonString(encodedString), triggerPoint, trackerRefId);

            if (response != null && response.get("errors") != null) {
                throw new Exception(response.get("errors").toString());
            }
        } catch (ConnectionException e) {
            throw e;
        } catch (JsonProcessingException | RuntimeException e) {
            throw e;
        }
    }

    @Override
    public void uploadSyncedPacket(@NonNull List<PacketUploadDTO> packets, HashMap<String, PacketUploadResponseDTO> response) throws Exception {
        for (PacketUploadDTO packet : packets) {
            try {
                Long startTime = System.nanoTime();
                LOGGER.debug("SESSION_ID", "PACKET_UPLOAD", "uploadSyncedPacket()", "Time Taken to start Packet Upload. Reference Id : " + packet.getPacketId() + " (" + TimeUnit.MILLISECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS) + " ms)");
                uploadPacket(packet, startTime, packet.getPacketId());
            } catch (Exception e) {
                throw e;
            }
        }
    }

        public void uploadPacket(@NonNull PacketUploadDTO packetUpload, Long startTime, String trackerRefid) throws Exception {
        File packet = FileUtils.getFile(packetUpload.getPacketPath() +
                RegistrationConstants.SLASH + packetUpload.getPacketId() + RegistrationConstants.ZIP_FILE_EXTENSION);
            LOGGER.debug("SESSION_ID", "PACKET_UPLOAD", "uploadPacket()", "Time Taken to fetch file from local storage. Reference Id : " + trackerRefid + " (" + TimeUnit.MILLISECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS) + " ms)");
        try {
            pushPacketWithRetryWrapper(packet, startTime, trackerRefid);
            LOGGER.debug("SESSION_ID", "PACKET_UPLOAD", "uploadPacket()", "Time Taken to complete packet upload. Reference Id : " + trackerRefid + " (" + TimeUnit.MILLISECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS) + " ms)");
        } catch (ConnectionException e) {
            e.printStackTrace();
        }
    }

        private String pushPacketWithRetryWrapper(File packet, Long startTime, String trackerRefid) throws ConnectionException {
        RetryCallback<String, ConnectionException> retryCallback = new RetryCallback<String, ConnectionException>() {
            @SneakyThrows
            @Override
            public String doWithRetry(RetryContext retryContext) {
                return pushPacket(packet, startTime, trackerRefid);
            }
        };
        return retryTemplate.execute(retryCallback);
    }

        private String pushPacket(File packet, Long startTime, String trackerRefid) throws ConnectionException, Exception {
        if (!packet.exists())
            throw new Exception("Packet Not Found in the Path " + packet.getAbsolutePath());

        LinkedMultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
        map.add(RegistrationConstants.PACKET_TYPE, new FileSystemResource(packet));
        HashMap<String, Object> response = (HashMap<String, Object>) restApiClient
                    .post(RegistrationConstants.PACKET_UPLOAD, map, RegistrationConstants.JOB_TRIGGER_POINT_USER, trackerRefid);


            if (response.get(RegistrationConstants.ERRORS) != null) {
            HashMap<String, String> error = ((List<HashMap<String, String>>) response.get(RegistrationConstants.ERRORS)).get(0);
            throw new Exception(error.get("errorCode") + " : " + error.get("message"));
        }

        if (response.get(RegistrationConstants.REST_RESPONSE_BODY) != null) {
            HashMap<String, Object> responseBody = (HashMap<String, Object>) response.get(RegistrationConstants.REST_RESPONSE_BODY);
            String status = (String) responseBody.get(RegistrationConstants.UPLOAD_STATUS);

            if(status.equals("Packet has reached Packet Receiver")) {
                Path path = Paths.get(System.getProperty("user.dir"), "home/Archieve");
                File archieveFile = path.toFile();
                if(!archieveFile.exists())
                    archieveFile.mkdirs();

                packet.renameTo(new File(path.toAbsolutePath().toString() + "//" + packet.getName()));
            }

            return status;
        }

        throw new Exception("Packet Upload Error : " + (new Gson()).toJson(response));
    }

        private String getEnvironmentProperty(String property) {
        return environment.getProperty(property);
    }
}
