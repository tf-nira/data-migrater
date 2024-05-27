package io.mosip.packet.core.util;

import io.mosip.kernel.biometrics.constant.BiometricType;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.config.biosdk.BioSDKConfig;
import io.mosip.packet.core.dto.biosdk.BioSDKRequestWrapper;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.spi.BioSdkApiFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.mosip.packet.core.constant.GlobalConfig.IS_ONLY_FOR_QUALITY_CHECK;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
public class BioSDKUtil {
    private static final Logger LOGGER = DataProcessLogger.getLogger(BioSDKUtil.class);

    @Autowired
    private BioSDKConfig bioSDKConfig;

    public String calculateQualityScore(BioSDKRequestWrapper requestWrapper, String key, String trackerColumn, Long startTime) throws Exception {
        String biosdkVendor = null;
        HashMap<String, String> csvMap = (HashMap<String, String>) requestWrapper.getInputObject();
        String calculatedScore = null;
        BiometricType biometricType = BiometricType.fromValue(requestWrapper.getBiometricType());

        try {
            if(IS_ONLY_FOR_QUALITY_CHECK) {
                Map<String, BioSdkApiFactory> bioSdkMap = bioSDKConfig.getBioSDKList().get(biometricType);
                LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Fetch BIOSDK List from Configuration " + trackerColumn + " - " + key + " " + TimeUnit.SECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS));

                for(Map.Entry<String, BioSdkApiFactory> bioSdkEntry : bioSdkMap.entrySet()) {
                    biosdkVendor = bioSdkEntry.getKey();
                    requestWrapper.setBiometricField(key + "_" + biosdkVendor);

                    LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Before Calling BIOSDK Call" + trackerColumn + " - " + key + " " + TimeUnit.SECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS));

                    Double score = bioSdkEntry.getValue().calculateBioQuality(requestWrapper);
                    LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "After Calling BIOSDK Call" + trackerColumn + " - " + key + " " + TimeUnit.SECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS));

                    String currentVal = csvMap.get(key);
                    if(bioSDKConfig.getBioSDKList().get(biometricType).size() > 1) {
                        if(currentVal == null)
                            currentVal = biosdkVendor + "(" + score.toString() + ")";
                        else
                            currentVal+= "," + biosdkVendor + "(" + score.toString() + ")";
                    } else {
                        currentVal = score.toString();
                    }
                    csvMap.put(key,  currentVal.toString());
                    calculatedScore = score.toString();
                    LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "After Update the Score into CSVMAP" + trackerColumn + " - " + key + " " + TimeUnit.SECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS));
                }
            } else {
                requestWrapper.setBiometricField(key);
                Double score = bioSDKConfig.getDefaultBioSDK().get(biometricType).calculateBioQuality(requestWrapper);
                csvMap.put(key, score.toString());
                calculatedScore = score.toString();
            }

            Long timeDifference = System.nanoTime()-startTime;
            LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "After Calculation of Quality from BIOSDK " + trackerColumn + " - " + key + " " + TimeUnit.SECONDS.convert(timeDifference, TimeUnit.NANOSECONDS));
            return calculatedScore;
        } catch (Exception e) {
            csvMap.put(key + "_" + biosdkVendor, e.getMessage());
            throw e;
        }
    }
}
