package io.mosip.packet.manager.util.mock.sbi.devicehelper;

import io.mosip.packet.core.constant.SBIConstant;
import io.mosip.packet.core.dto.mockmds.*;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.util.mockmds.CryptoUtility;
import io.mosip.packet.core.util.mockmds.StringHelper;
import io.mosip.packet.manager.service.mockmds.SBIJsonInfo;
import io.mosip.packet.manager.util.mock.sbi.JwtUtility;
import io.mosip.packet.manager.util.mock.sbi.devicehelper.face.SBIFaceHelper;
import io.mosip.packet.manager.util.mock.sbi.devicehelper.finger.slap.SBIFingerSlapHelper;
import io.mosip.packet.manager.util.mock.sbi.devicehelper.iris.binacular.SBIIrisDoubleHelper;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.codehaus.jackson.map.annotate.JsonSerialize;
import io.mosip.kernel.core.logger.spi.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
public class MockDeviceUtil {

    private Logger LOGGER = DataProcessLogger.getLogger(MockDeviceUtil.class);

    @Autowired
    private SBIFingerSlapHelper sbiFingerSlapHelper;

    @Autowired
    private SBIIrisDoubleHelper sbiIrisDoubleHelper;

    @Autowired
    private SBIFaceHelper sbiFaceHelper;

    @Autowired
    Environment env;

    @Autowired
    private SBIJsonInfo sbiJsonInfo;

    protected HashMap<String, SBIDeviceHelper> deviceHelpers = new HashMap<>();

    private LocalDateTime lastInitializedTime;

    @Value("${mosip.mock.sbi.device.re-initialization.minutes:5}")
    private Integer reInitMiniutes;

    public BioMetricsDto getBiometricData (String deviceTypeName, CaptureRequestDto requestObject, String bioValue,
                                            String lang, String errorCode) throws JsonGenerationException, JsonMappingException, IOException, NoSuchAlgorithmException, DecoderException
    {
        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "MockDeviceUtil :: getBiometricData():: entry");
        SBIDeviceHelper deviceHelper = getDeviceHelper(deviceTypeName);
        DeviceInfo deviceInfo = deviceHelper.getDeviceInfo();

        CaptureRequestDeviceDetailDto captureRequestDeviceDetailDto = requestObject.getBio();
        BioMetricsDto biometric = new BioMetricsDto ();
        biometric.setSpecVersion(requestObject.getSpecVersion());

        biometric.setError(new ErrorInfo(errorCode, sbiJsonInfo.getErrorDescription (lang, errorCode)));

        BioMetricsDataDto biometricData = new BioMetricsDataDto ();
        biometricData.setDeviceCode(deviceInfo.getDeviceCode());
        biometricData.setDigitalId(deviceInfo.getDigitalId());
        biometricData.setDeviceServiceVersion(deviceInfo.getServiceVersion());
        biometricData.setBioType(captureRequestDeviceDetailDto.getType());
        biometricData.setBioSubType(captureRequestDeviceDetailDto.getBioSubType());

        biometricData.setPurpose(requestObject.getPurpose());
        biometricData.setEnv(requestObject.getEnv());
        biometricData.setBioValue(bioValue);
        biometricData.setTimestamp(CryptoUtility.getTimestamp());


        biometricData.setRequestedScore(captureRequestDeviceDetailDto.getRequestedScore() + "");
        biometricData.setTransactionId(requestObject.getTransactionId());

        ObjectMapper mapper = new ObjectMapper ();
        SerializationConfig config = mapper.getSerializationConfig();
        config.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
        mapper.setSerializationConfig(config);

        String currentBioData = mapper.writeValueAsString(biometricData);

        //base64 signature of the data block. base64 signature of the hash element
        String dataBlockSignBase64 = deviceHelper.getSignBioMetricsDataDto (deviceHelper.getDeviceType(), deviceHelper.getDeviceSubType(), currentBioData);
        biometric.setData (dataBlockSignBase64);

        byte[] previousBioDataHash = null;
        byte [] previousDataByteArr = StringHelper.toUtf8ByteArray ("");
        previousBioDataHash = generateHash(previousDataByteArr);
        //instead of BioData, bioValue (before encrytion in case of Capture response) is used for computing the hash.
        byte [] currentDataByteArr = StringHelper.base64UrlDecode(bioValue);
        // Here Byte Array
        byte[] currentBioDataHash = generateHash (currentDataByteArr);
        byte[] finalBioDataHash = new byte[currentBioDataHash.length + previousBioDataHash.length];
        System.arraycopy (previousBioDataHash, 0, finalBioDataHash, 0, previousBioDataHash.length);
        System.arraycopy (currentBioDataHash, 0, finalBioDataHash, previousBioDataHash.length, currentBioDataHash.length);

        biometric.setHash(toHex (generateHash (finalBioDataHash)));
        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "MockDeviceUtil :: getBiometricData():: exit");

        return biometric;
    }

    public String toHex(byte[] bytes) {
        return Hex.encodeHexString(bytes).toUpperCase();
    }

    private final String HASH_ALGORITHM_NAME = "SHA-256";
    public byte[] generateHash(final byte[] bytes) throws NoSuchAlgorithmException{
        MessageDigest messageDigest = MessageDigest.getInstance(HASH_ALGORITHM_NAME);
        return messageDigest.digest(bytes);
    }

    public void initDeviceHelpers() {
        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "MockDeviceUtil :: initDeviceHelpers():: entry");
        this.deviceHelpers.put(SBIConstant.MOSIP_BIOMETRIC_TYPE_FINGER , sbiFingerSlapHelper.getInstance(env));
        this.deviceHelpers.put(SBIConstant.MOSIP_BIOMETRIC_TYPE_IRIS , sbiIrisDoubleHelper.getInstance(env));
        this.deviceHelpers.put(SBIConstant.MOSIP_BIOMETRIC_TYPE_FACE , sbiFaceHelper.getInstance(env));
        lastInitializedTime = LocalDateTime.now();
        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "MockDeviceUtil :: initDeviceHelpers():: exit");
    }

    public void resetDevices() {
        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "MockDeviceUtil :: resetDevices():: entry");
        for(Map.Entry<String, SBIDeviceHelper> entry : this.deviceHelpers.entrySet())
            entry.getValue().resetDevices();
        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "MockDeviceUtil :: resetDevices():: exit");
    }

    public SBIDeviceHelper getDeviceHelper (String deviceTypeName)
    {
        if(lastInitializedTime ==null || (ChronoUnit.MINUTES.between(lastInitializedTime, LocalDateTime.now()) > reInitMiniutes)) {
            resetDevices();
            initDeviceHelpers();
        }

        if (this.deviceHelpers != null && this.deviceHelpers.size() >= 0)
        {
            if (this.deviceHelpers.containsKey(deviceTypeName) )
            {
                return this.deviceHelpers.get(deviceTypeName);
            }
        }

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
        }

        return getDeviceHelper (deviceTypeName);
    }
/*
    private String processRCaptureInfo(CaptureRequestDto requestObject) {
        String response = null;
        String lang = "en";
        String specVersion = "";
        SBIDeviceHelper deviceHelper = null;

        try {
            String deviceId = "", deviceType = "", env = "";
            int deviceSubId = 0;
            List<CaptureRequestDeviceDetailDto> mosipBioRequest = null;
            // if Null Throw Errors here
            if (requestObject != null)
            {
                mosipBioRequest = requestObject.getBio();
                if (mosipBioRequest != null && mosipBioRequest.size() > 0)
                {
                    deviceId = requestObject.getBio().get(0).getDeviceId();
                    deviceSubId = Integer.parseInt(requestObject.getBio().get(0).getDeviceSubId());
                    deviceType = requestObject.getBio().get(0).getType();
                    env = requestObject.getEnv();
                }
            }

            LOGGER.info("processRCaptureInfo :: deviceId :: "+ deviceId + " :: deviceSubId ::" + deviceSubId);
            if (deviceType != null && deviceType.trim().length() > 0 &&
                    !(
                            deviceType.trim().equalsIgnoreCase(SBIConstant.MOSIP_BIOMETRIC_TYPE_FINGER) ||
                                    deviceType.trim().equalsIgnoreCase(SBIConstant.MOSIP_BIOMETRIC_TYPE_IRIS) ||
                                    deviceType.trim().equalsIgnoreCase(SBIConstant.MOSIP_BIOMETRIC_TYPE_FACE))
            )
            {
                throw new Exception("Device Type can be only (Finger/Iris/Face)");
            }

            deviceHelper = getDeviceHelperForDeviceId (deviceId);
            if (deviceHelper == null || deviceHelper.getDeviceInfo() == null)
            {
                throw new Exception("Device is not connected or not Registered for the deviceId given in RCapture request object");
            }

            String bioType = mosipBioRequest.get(0).getType();
            String [] bioException = mosipBioRequest.get(0).getException();// Bio exceptions

            if (deviceHelper.getDeviceInfo() != null)
            {
                deviceHelper.initDevice();
                deviceHelper.setDeviceId(deviceId);
                deviceHelper.setDeviceSubId(deviceSubId);
                deviceHelper.setDeviceStatus(SBIConstant.DEVICE_STATUS_ISBUSY);
            }


            int requestScore = Integer.parseInt(mosipBioRequest.get(0).getRequestedScore() + "");

            specVersion = requestObject.getSpecVersion();
            int returnCode = -1;
            boolean captureLiveStreamEnded = false;
            while (true)
            {
                if (!captureStarted)
                {
                    deviceHelper.setProfileId(mockService.getProfileId());

                    if (bioException != null && !bioType.equals(SBIConstant.MOSIP_BIOMETRIC_TYPE_FACE))
                        deviceHelper.getCaptureInfo().getBioExceptionInfo().initBioException(bioException);

                    deviceHelper.getCaptureInfo().setRequestScore(requestScore);
                    deviceHelper.getCaptureInfo().setCaptureStarted(true);
                    captureStarted = true;
                }
                delay(delay);
                try
                {
                    if (System.currentTimeMillis () > endTime)
                    {
                        captureTimeOut = true;
                        break;
                    }
                    // acquiring the lock
                    if (semaphore != null)
                        semaphore.acquire();

                    if (deviceHelper.getCaptureInfo() == null)
                    {
                        captureLiveStreamEnded = true;
                        break;
                    }

                    returnCode = deviceHelper.getBioCapture(false);

                    if (deviceHelper.getCaptureInfo() != null && deviceHelper.getCaptureInfo().isCaptureCompleted())
                    {
                        break;
                    }
                }
                catch (Exception ex)
                { }
                finally
                {
                    try
                    {
                        if (semaphore != null)
                            semaphore.release();
                    }
                    catch (Exception ex)
                    { }
                }

                Thread.sleep (30);
            }

            if (captureLiveStreamEnded)
            {
                response = SBIJsonInfo.getCaptureErrorJson  (specVersion, lang, "700", "", true);
            }
            else if (captureTimeOut)
            {
                response = SBIJsonInfo.getCaptureErrorJson  (specVersion, lang, "701", "", true);
                if (deviceHelper.getCaptureInfo() == null)
                    deviceHelper.getCaptureInfo().setCaptureCompleted(true);
            }
            else
            {
                List<BioMetricsDto> biometrics = getBioMetricsDtoList (lang, requestObject, deviceHelper, deviceSubId, false);
                if (biometrics != null && biometrics.size() > 0)
                {
                    RCaptureResponse captureResponse = new RCaptureResponse ();
                    captureResponse.setBiometrics(biometrics);

                    ObjectMapper mapper = new ObjectMapper ();
                    SerializationConfig config = mapper.getSerializationConfig();
                    config.setSerializationInclusion(JsonSerialize.Inclusion.NON_NULL);
                    mapper.setSerializationConfig(config);

                    response = mapper.writeValueAsString(captureResponse);
                }
                else
                {
                    response = SBIJsonInfo.getCaptureErrorJson  (specVersion, lang, "708", "", true);
                }

                deviceHelper.deInitDevice();
                deviceHelper.setDeviceStatus(SBIConstant.DEVICE_STATUS_ISREADY);
            }

            if (deviceHelper.getCaptureInfo() != null)
            {
                deviceHelper.getCaptureInfo().getBioExceptionInfo().deInitBioException();
                // When Capture is called After LiveStreaming is called
                // DeInit is called in Livestream method
                if (deviceHelper.getCaptureInfo().isLiveStreamStarted())
                {
                    deviceHelper.getCaptureInfo().setCaptureStarted(false);
                    deviceHelper.getCaptureInfo().setCaptureCompleted(true);
                }
                // DeInit When Capture is called Directly
                else
                {
                    deviceHelper.deInitDevice();
                    deviceHelper.setDeviceStatus(SBIConstant.DEVICE_STATUS_ISREADY);
                }
            }
        }
        catch (Exception ex)
        {
            response = SBIJsonInfo.getCaptureErrorJson (specVersion, lang, "710", "", true);
            LOGGER.error("processRCaptureInfo", ex);
        }
        finally
        {
            try
            {
                if (semaphore != null)
                    semaphore.release ();
            }
            catch (Exception ex)
            {
            }
        }
        return response;
    }

    private SBIDeviceHelper getDeviceHelperForDeviceId(String deviceId) {

        SBIDeviceHelper deviceHelper = null;

        deviceHelper = (SBIFaceHelper) mockService.getDeviceHelper(SBIConstant.MOSIP_BIOMETRIC_TYPE_FACE + "_" + SBIConstant.MOSIP_BIOMETRIC_SUBTYPE_FACE);
        deviceHelper.initDeviceDetails();
        if (deviceHelper.getDeviceInfo() != null && deviceHelper.getDeviceInfo().getDeviceId().trim().equals(deviceId.trim()))
        {
            return deviceHelper;
        }

        switch (mockService.getPurpose())
        {
            case SBIConstant.PURPOSE_REGISTRATION:
                deviceHelper = (SBIFingerSlapHelper) mockService.getDeviceHelper(SBIConstant.MOSIP_BIOMETRIC_TYPE_FINGER + "_" + SBIConstant.MOSIP_BIOMETRIC_SUBTYPE_FINGER_SLAP);
                deviceHelper.initDeviceDetails();
                if (deviceHelper.getDeviceInfo() != null && deviceHelper.getDeviceInfo().getDeviceId().trim().equals(deviceId.trim()))
                {
                    return deviceHelper;
                }

                deviceHelper = (SBIIrisDoubleHelper) mockService.getDeviceHelper(SBIConstant.MOSIP_BIOMETRIC_TYPE_IRIS + "_" + SBIConstant.MOSIP_BIOMETRIC_SUBTYPE_IRIS_DOUBLE);
                deviceHelper.initDeviceDetails();
                if (deviceHelper.getDeviceInfo() != null && deviceHelper.getDeviceInfo().getDeviceId().trim().equals(deviceId.trim()))
                {
                    return deviceHelper;
                }
                break;
            case SBIConstant.PURPOSE_AUTH:
                deviceHelper = (SBIFingerSingleHelper) mockService.getDeviceHelper(SBIConstant.MOSIP_BIOMETRIC_TYPE_FINGER + "_" + SBIConstant.MOSIP_BIOMETRIC_SUBTYPE_FINGER_SINGLE);
                deviceHelper.initDeviceDetails();
                if (deviceHelper.getDeviceInfo() != null && deviceHelper.getDeviceInfo().getDeviceId().trim().equals(deviceId.trim()))
                {
                    return deviceHelper;
                }

                deviceHelper = (SBIIrisSingleHelper) mockService.getDeviceHelper(SBIConstant.MOSIP_BIOMETRIC_TYPE_IRIS + "_" + SBIConstant.MOSIP_BIOMETRIC_SUBTYPE_IRIS_SINGLE);
                deviceHelper.initDeviceDetails();
                if (deviceHelper.getDeviceInfo() != null && deviceHelper.getDeviceInfo().getDeviceId().trim().equals(deviceId.trim()))
                {
                    return deviceHelper;
                }
                break;
        }

        return null;
    }*/
}
