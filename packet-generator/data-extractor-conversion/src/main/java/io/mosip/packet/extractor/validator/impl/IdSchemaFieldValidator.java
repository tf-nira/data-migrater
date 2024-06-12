package io.mosip.packet.extractor.validator.impl;

import io.mosip.packet.core.constant.FieldCategory;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.dto.dbimport.FieldFormatRequest;
import io.mosip.packet.core.dto.dbimport.TableRequestDto;
import io.mosip.packet.core.exception.ApisResourceAccessException;
import io.mosip.packet.core.util.CommonUtil;
import io.mosip.packet.extractor.util.PacketCreator;
import io.mosip.packet.extractor.validator.Validator;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
public class IdSchemaFieldValidator implements Validator {

    @Autowired
    private CommonUtil commonUtil;


    List<String> idFields;

    private List<String> getIdFields() throws ApisResourceAccessException, IOException, ParseException {
        if (idFields == null) {
            idFields = new ArrayList<>();
            HashMap<String, Object> idSchema = commonUtil.getLatestIdSchema();

            for(Object obj : (List)idSchema.get("schema")) {
                Map<String, Object> map = (Map<String, Object>) obj;
                String id = map.get("id").toString();
                String type = map.get("type").toString();

                if (type.equals("biometricsType")) {
                    List<String> bioAttributes = (List<String>) map.get("bioAttributes");
                    for(String attribute : bioAttributes)
                        idFields.add(id + "_" + attribute);
                } else {
                    idFields.add(id);
                }
            }
        }
        return idFields;
    }

    @Override
    public Boolean validate(DBImportRequest dbImportRequest) throws Exception {
        List<String> idFieldsList = getIdFields();
        List<String> nonIdSchemaTableFieldsMap = commonUtil.getNonIdSchemaFieldsMap();

        for(FieldFormatRequest fieldFormatRequest : dbImportRequest.getColumnDetails()) {
            if(nonIdSchemaTableFieldsMap == null || (nonIdSchemaTableFieldsMap != null && nonIdSchemaTableFieldsMap.size() > 0 && !nonIdSchemaTableFieldsMap.contains(fieldFormatRequest.getFieldToMap())))
                for(String fieldName : fieldFormatRequest.getFieldToMap().split(","))
                    if(!idFieldsList.contains(fieldName))
                        throw new Exception(fieldFormatRequest.getFieldToMap() + " is not found in Id Schema.");

            if(fieldFormatRequest.getUseAsHandle() != null && fieldFormatRequest.getUseAsHandle() && dbImportRequest.getIgnoreIdSchemaFields() != null && dbImportRequest.getIgnoreIdSchemaFields().size() > 0 && dbImportRequest.getIgnoreIdSchemaFields().contains(fieldFormatRequest.getFieldToMap()))
                throw new Exception(fieldFormatRequest.getFieldToMap() + " is Configured as handle and its mandatory to capture. Please remove same from Ignore ID Schema List.");
        }
        return true;
    }
}
