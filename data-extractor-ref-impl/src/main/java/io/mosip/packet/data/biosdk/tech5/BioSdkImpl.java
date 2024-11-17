package io.mosip.packet.data.biosdk.tech5;

import com.google.gson.Gson;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.ApiName;
import io.mosip.packet.core.dto.ResponseWrapper;
import io.mosip.packet.core.dto.biosdk.*;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.service.DataRestClientService;
import io.mosip.packet.core.spi.BioSdkApiFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static io.mosip.packet.core.constant.GlobalConfig.IS_ONLY_FOR_QUALITY_CHECK;
import static io.mosip.packet.core.constant.GlobalConfig.WRITE_BIOSDK_RESPONSE;

@Component
public class BioSdkImpl implements BioSdkApiFactory {

    @Autowired
    private DataRestClientService restApiClient;

    private static final Logger LOGGER = DataProcessLogger.getLogger(BioSdkImpl.class);

    @Override
    public Double calculateBioQuality(BioSDKRequestWrapper bioSDKRequestWrapper, String trakerRefid) throws Exception {
        SegmentDto segment = new SegmentDto();
        segment.setSegments(new ArrayList<>());
        segment.getSegments().add(bioSDKRequestWrapper.getSegments().get(0));
        segment.setOthers(new OtherDto());
        QualityCheckRequest request = new QualityCheckRequest();
        request.setSample(segment);
        request.setModalitiesToCheck(new ArrayList<>());
        request.getModalitiesToCheck().add(bioSDKRequestWrapper.getBiometricType().toUpperCase());

        String requestText = (new Gson()).toJson(request);
        String encodedRequest = Base64.getEncoder().encodeToString(requestText.getBytes(StandardCharsets.UTF_8));
        BioSDKRequest bioSDKRequest = new BioSDKRequest();
        bioSDKRequest.setVersion("1.0");
        bioSDKRequest.setRequest(encodedRequest);

        ResponseWrapper response= (ResponseWrapper) restApiClient.postApi(ApiName.BIOSDK_QUALITY_CHECK, null, "", bioSDKRequest, ResponseWrapper.class, trakerRefid);
        if(WRITE_BIOSDK_RESPONSE) {
            HashMap<String, String> csvMap = (HashMap<String, String>) bioSDKRequestWrapper.getInputObject();
            csvMap.put(bioSDKRequestWrapper.getBiometricField(),  (new Gson()).toJson(response));
        }
        HashMap<String, Object> bioSDKResponse = (HashMap<String, Object>) response.getResponse();
        if(bioSDKResponse.get("statusCode").equals(200)) {
            HashMap<String, Object> resp = (HashMap<String, Object>) bioSDKResponse.get("response");
            HashMap<String, Object> scoreMap = (HashMap<String, Object>) resp.get("scores");
            HashMap<String, Object> modalityMap = (HashMap<String, Object>) scoreMap.get(bioSDKRequestWrapper.getBiometricType().toUpperCase());
            return (Double) modalityMap.get("score");
        } else {
            LOGGER.error("Error While Calling BIOSDK for Quality Check for Modality " + (new Gson()).toJson(response));

            if(!IS_ONLY_FOR_QUALITY_CHECK) {
                throw new Exception("Error While Calling BIOSDK for Quality Check for Modality ");
            } else {
                return Double.valueOf(0);
            }
        }
    }
}
