package io.mosip.packet.data.convertion;

import io.mosip.commons.packet.constants.Biometric;
import io.mosip.kernel.biometrics.constant.BiometricType;
import io.mosip.kernel.biometrics.entities.BIR;
import io.mosip.kernel.biometrics.entities.SingleAnySubtypeType;
import io.mosip.kernel.biometrics.spi.CbeffUtil;
import io.mosip.kernel.core.http.RequestWrapper;
import io.mosip.kernel.core.util.StringUtils;
import io.mosip.kernel.cryptomanager.dto.CryptomanagerRequestDto;
import io.mosip.packet.core.constant.ApiName;
import io.mosip.packet.core.dto.ResponseWrapper;
import io.mosip.packet.core.service.DataRestClientService;
import io.mosip.packet.core.spi.BioDocApiFactory;
import io.mosip.packet.core.util.DateUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@ConditionalOnProperty(value = "mosip.packet.bio.doc.data.converter.classname", havingValue = "BioDataCbeffUtil")
public class BioDataCbeffUtil implements BioDocApiFactory {

    @Autowired
    CbeffUtil cbeffUtil;

    @Autowired
    private DataRestClientService restApiClient;

    private String APP_ID = "ID_REPO";

    private String REFERENCE_ID = "biometric_data";

    @Override
    public Map<String, byte[]> getBioData(byte[] byteval, String fieldName) throws Exception {
        Map<String, byte[]> map = new HashMap<>();

        CryptomanagerRequestDto requestDto = new CryptomanagerRequestDto();
        requestDto.setApplicationId(APP_ID);
        requestDto.setReferenceId(REFERENCE_ID);
        requestDto.setData(new String(byteval));
        requestDto.setTimeStamp(DateUtils.getUTCCurrentDateTime());
        RequestWrapper request = new RequestWrapper();
        request.setRequest(requestDto);
        ResponseWrapper responseWrapper = (ResponseWrapper) restApiClient.postApi(ApiName.KERNEL_DECRYPT, null, null, request, ResponseWrapper.class, MediaType.APPLICATION_JSON, "BioDataCbeffUtil Decryption");

        if(responseWrapper.getErrors() != null && responseWrapper.getErrors().size() > 0) {

        } else {
            HashMap<String, Object> responseDto = (HashMap<String, Object>) responseWrapper.getResponse();
      //      String subType = subTypeList.get(subTypeList.size()-1);

            List<BIR> data =  cbeffUtil.getBIRDataFromXML(Base64.getUrlDecoder().decode(responseDto.get("data").toString()));

            for(String name : fieldName.split(",")) {
                String[] typeArray = name.split("_");
                BiometricType bioType = Biometric.getSingleTypeByAttribute(typeArray[1]);
                List<String> subTypeList = getSubTypes(bioType, typeArray[1].toString());

                for(BIR bir : data) {
                    List<String> birSubTypeList =  bir.getBdbInfo().getSubtype();
                    if(subTypeList.toString().equals(birSubTypeList.toString())) {
                        map.put(name, bir.getBdb());
                    }
                }
            }
        }

        return map;
    }

    private List<String> getSubTypes(BiometricType biometricType, String bioAttribute) {
        List<String> subtypes = new LinkedList<>();
        switch (biometricType) {
            case FINGER:
                subtypes.add(bioAttribute.contains("left") ? SingleAnySubtypeType.LEFT.value()
                        : SingleAnySubtypeType.RIGHT.value());
                if (bioAttribute.toLowerCase().contains("thumb"))
                    subtypes.add(SingleAnySubtypeType.THUMB.value());
                else {
                    String val = bioAttribute.toLowerCase().replace("left", "").replace("right", "");
                    subtypes.add(SingleAnySubtypeType.fromValue(StringUtils.capitalizeFirstLetter(val).concat("Finger"))
                            .value());
                }
                break;
            case IRIS:
                subtypes.add(bioAttribute.contains("left") ? SingleAnySubtypeType.LEFT.value()
                        : SingleAnySubtypeType.RIGHT.value());
                break;

            case EXCEPTION_PHOTO:
            case FACE:
                break;
        }
        return subtypes;
    }

    @Override
    public Map<String, byte[]> getDocData(byte[] byteval, String fieldName) {
        Map<String, byte[]> map = new HashMap<>();
        map.put(fieldName, byteval);
        return map;
    }
}
