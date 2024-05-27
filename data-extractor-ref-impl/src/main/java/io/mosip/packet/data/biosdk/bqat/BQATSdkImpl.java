package io.mosip.packet.data.biosdk.bqat;

import com.google.gson.Gson;
import io.mosip.kernel.biometrics.entities.BIR;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.ApiName;
import io.mosip.packet.core.dto.biosdk.BioSDKRequestWrapper;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.service.DataRestClientService;
import io.mosip.packet.core.spi.BioSdkApiFactory;
import io.mosip.packet.data.biosdk.bqat.constant.BQATFileType;
import io.mosip.packet.data.biosdk.bqat.constant.BQATModalityType;
import io.mosip.packet.data.biosdk.bqat.dto.BQATRequest;
import io.mosip.packet.data.biosdk.bqat.dto.BQATResponse;
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
import static io.mosip.packet.core.constant.GlobalConfig.WRITE_BIOSDK_RESPONSE;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
public class BQATSdkImpl implements BioSdkApiFactory {

    @Autowired
    private DataRestClientService restApiClient;

    private static final Logger LOGGER = DataProcessLogger.getLogger(BQATSdkImpl.class);

    @Value("${mosip.biometric.sdk.providers.bqat.port.ranges:8848}")
    private String allowedPortRange;

    private LinkedList<String> portRange;

    @Autowired
    private Environment env;

    @PostConstruct
    public void initialize() {
        portRange = new LinkedList<>();
        String[] rangeString = allowedPortRange.split(":");
        Integer rangeFrom = Integer.parseInt(rangeString[0]);
        Integer rangeTo = rangeString.length > 1 ? Integer.parseInt(rangeString[1]) : Integer.parseInt(rangeString[0]);

        for(int i = rangeFrom; i<=rangeTo; i++)
            portRange.add(String.valueOf(i));
    }

    @Override
    public Double calculateBioQuality(BioSDKRequestWrapper bioSDKRequestWrapper) throws Exception {
        Long startTime = System.nanoTime();
        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Entering BIOSDK for Quality Calculation" + TimeUnit.SECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS));

        BQATRequest request = new BQATRequest();
        request.setModality(BQATModalityType.valueOf(bioSDKRequestWrapper.getBiometricType().toUpperCase()).getModality());
        request.setType(BQATFileType.valueOf(bioSDKRequestWrapper.getFormat().toUpperCase()).getType());
        request.setData(Base64.getEncoder().encodeToString(((BIR)bioSDKRequestWrapper.getSegments().get(0)).getBdb()));
        request.setId(UUID.randomUUID().toString());
        request.setTimestamp(LocalDateTime.now(ZoneId.of("UTC")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")));
        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Request Preparation Completed " + TimeUnit.SECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS));
        String queryParameterValue = portRange.removeFirst();
        portRange.addLast(queryParameterValue);
        String url = env.getProperty(ApiName.BQAT_BIOSDK_QUALITY_CHECK.toString());
        url = url.replace("{PORT_NO}", queryParameterValue);
        BQATResponse response= (BQATResponse) restApiClient.postApi(url, null, null, request, BQATResponse.class, false, ApiName.BQAT_BIOSDK_QUALITY_CHECK);
        HashMap<String, Object> bioSDKResponse = (HashMap<String, Object>) response.getResults();
        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Rest Call Executed Successfully " + TimeUnit.SECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS));
        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken to call BIOSDK is " + TimeUnit.SECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS));

        if(bioSDKResponse != null) {
            try {
                if(bioSDKRequestWrapper.getIsOnlyForQualityCheck()) {
                    if(BQATModalityType.valueOf(bioSDKRequestWrapper.getBiometricType().toUpperCase()).equals(BQATModalityType.FACE))
                        return Double.valueOf(bioSDKResponse.get("quality").toString());
                    else if(BQATModalityType.valueOf(bioSDKRequestWrapper.getBiometricType().toUpperCase()).equals(BQATModalityType.IRIS))
                        return Double.valueOf(bioSDKResponse.get("quality").toString());
                    else
                        return Double.valueOf(bioSDKResponse.get("NFIQ2").toString());
                } else {
                    if(BQATModalityType.valueOf(bioSDKRequestWrapper.getBiometricType().toUpperCase()).equals(BQATModalityType.FACE))
                        return Double.valueOf(bioSDKResponse.get("quality").toString());
                    else if(BQATModalityType.valueOf(bioSDKRequestWrapper.getBiometricType().toUpperCase()).equals(BQATModalityType.IRIS))
                        return Double.valueOf(bioSDKResponse.get("quality").toString());
                    else
                        return Double.valueOf(bioSDKResponse.get("NFIQ2").toString());
                }
            } catch (Exception e) {
                if(!IS_ONLY_FOR_QUALITY_CHECK)
                    throw e;
                else
                    return Double.valueOf(0);
            } finally {
                LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Existing BIOSDK for Quality Calculation" + TimeUnit.SECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS));

                if(WRITE_BIOSDK_RESPONSE) {
                    HashMap<String, String> csvMap = (HashMap<String, String>) bioSDKRequestWrapper.getInputObject();
                    csvMap.put(bioSDKRequestWrapper.getBiometricField(),  (new Gson()).toJson(bioSDKResponse));
                }
                LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Writing Response into CSVMAP" + TimeUnit.SECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS));

            }
        } else {
            LOGGER.error("Error While Calling BIOSDK for Quality Check for Modality " + (new Gson()).toJson(response));
            if(!IS_ONLY_FOR_QUALITY_CHECK)
                throw new Exception("Error While Calling BIOSDK for Quality Check for Modality ");
            else
                return Double.valueOf(0);
        }
    }
}
