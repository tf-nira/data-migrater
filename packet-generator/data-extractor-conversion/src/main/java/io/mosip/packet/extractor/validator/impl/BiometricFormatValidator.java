package io.mosip.packet.extractor.validator.impl;

import io.mosip.packet.core.constant.BioSubType;
import io.mosip.packet.core.constant.FieldCategory;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.dto.dbimport.FieldFormatRequest;
import io.mosip.packet.core.dto.dbimport.IndividualBiometricFormat;
import io.mosip.packet.core.exception.ApisResourceAccessException;
import io.mosip.packet.core.util.CommonUtil;
import io.mosip.packet.extractor.validator.Validator;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class BiometricFormatValidator implements Validator {

    @Autowired
    private CommonUtil commonUtil;


    HashMap<String, BioSubType> idFields;

    private HashMap<String, BioSubType> getIdFields() throws ApisResourceAccessException, IOException, ParseException {
        if (idFields == null) {
            idFields = new HashMap<>();
            HashMap<String, Object> idSchema = commonUtil.getLatestIdSchema();

            for(Object obj : (List)idSchema.get("schema")) {
                Map<String, Object> map = (Map<String, Object>) obj;
                String id = map.get("id").toString();
                String type = map.get("type").toString();

                if (type.equals("biometricsType")) {
                    List<String> bioAttributes = (List<String>) map.get("bioAttributes");
                    for(String attribute : bioAttributes)
                        idFields.put(id + "_" + attribute, BioSubType.getBioSubType(attribute));
                }
            }
        }
        return idFields;
    }

    @Override
    public Boolean validate(DBImportRequest dbImportRequest) throws Exception {
        HashMap<String, BioSubType> idFieldsList = getIdFields();
        List<BioSubType> availableFormat = new ArrayList<>();

        for(FieldFormatRequest fieldFormatRequest : dbImportRequest.getColumnDetails()) {
            if(fieldFormatRequest.getIndividualBiometricFormat() != null && !fieldFormatRequest.getIndividualBiometricFormat().isEmpty()) {
                for(IndividualBiometricFormat format : fieldFormatRequest.getIndividualBiometricFormat())
                    availableFormat.add(format.getSubType());

                if(fieldFormatRequest.getFieldCategory().equals(FieldCategory.BIO)) {
                    String[] fieldNames = fieldFormatRequest.getFieldToMap().split(",");

                    for(String fieldName : fieldNames) {
                        if(!availableFormat.contains(idFieldsList.get(fieldName)))
                            throw new Exception("Individual Biometric Format not found for " + fieldName + " in APIRequest.json.");
                    }
                }
            }
        }
        return true;
    }
}
