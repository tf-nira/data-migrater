package io.mosip.packet.extractor.util;

import io.mosip.packet.core.constant.ValidatorEnum;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.extractor.validator.Validator;
import io.mosip.packet.extractor.validator.impl.BiometricFormatValidator;
import io.mosip.packet.extractor.validator.impl.FilterValidation;
import io.mosip.packet.extractor.validator.impl.IdSchemaFieldValidator;
import io.mosip.packet.extractor.validator.impl.OrderByValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;

@Component
@Import({IdSchemaFieldValidator.class, FilterValidation.class})
public class ValidationUtil {

    @Autowired
    private IdSchemaFieldValidator idSchemaFieldValidator;

    @Autowired
    private FilterValidation filterValidation;

    @Autowired
    private OrderByValidator orderByValidator;

    @Autowired
    private BiometricFormatValidator biometricFormatValidator;

    private HashMap<ValidatorEnum, Validator> validatorList = null;

    public HashMap<ValidatorEnum, Validator> getValidatorMap() {
        if(validatorList == null) {
            validatorList= new HashMap<>();
            validatorList.put(ValidatorEnum.ID_SCHEMA_VALIDATOR, idSchemaFieldValidator);
            validatorList.put(ValidatorEnum.FILTER_VALIDATOR, filterValidation);
            validatorList.put(ValidatorEnum.ORDERBY_VALIDATOR, orderByValidator);
            validatorList.put(ValidatorEnum.BIOMETRIC_FORMAT_VALIDATOR, biometricFormatValidator);
        }
        return validatorList;
    }


    public void validateRequest(DBImportRequest dbImportRequest, List<ValidatorEnum> validationList) throws Exception {
        for (ValidatorEnum validatorEnum : validationList)
            getValidatorMap().get(validatorEnum).validate(dbImportRequest);
    }
}
