package io.mosip.packet.data.service;

import com.google.gson.Gson;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.ApiName;
import io.mosip.packet.core.constant.LoggerFileConstant;
import io.mosip.packet.core.dto.ResponseWrapper;
import io.mosip.packet.core.exception.ApisResourceAccessException;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.service.DataRestClientService;
import io.mosip.packet.data.dto.IdRequestDto;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
public class ImportIdentityServiceImpl implements ImportIdentityService {

    private final Logger logger = DataProcessLogger.getLogger(ImportIdentityServiceImpl.class);

    @Autowired
    private DataRestClientService dataRestClientService;

    @Override
    public ResponseWrapper importIdentity(IdRequestDto idRequestDto) {
        //UIN Generation for every new request.
        logger.info("Writer Request: {}", idRequestDto);
        generateUIN(idRequestDto);
        return addIdentity(idRequestDto);
    }

    private ResponseWrapper addIdentity(IdRequestDto idRequestDto) {
        //logger.info("Add Identity Request: {}", (new Gson()).toJson(idRequestDto));
        try {
            Long startTime = System.nanoTime();
            ResponseWrapper response = (ResponseWrapper) dataRestClientService.postApi(ApiName.ADD_IDENTITY,
                    null, "", "", idRequestDto, ResponseWrapper.class);
            logger.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken for Add Identity Api Call"+ TimeUnit.SECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS));

            return response;
        } catch (ApisResourceAccessException e) {
            logger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.APPLICATIONID.toString(),
                    LoggerFileConstant.APPLICATIONID.toString(), e.getMessage() + ExceptionUtils.getStackTrace(e));
        }
        logger.info("Add Identity completed.");
        return null;
    }

    private void generateUIN(IdRequestDto idRequestDto) {
        try {
            // UIN generation
            Long startTime = System.nanoTime();
            ResponseWrapper response = (ResponseWrapper) dataRestClientService.getApi(ApiName.GET_UIN, null, "", "", ResponseWrapper.class);
            logger.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Time Taken for generate UIN Api Call" + TimeUnit.SECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS));
            if (response != null && response.getResponse() != null) {
                Map<String, String> responseMap = (Map<String, String>) response.getResponse();
                String uin = responseMap.get("uin");
                ((Map<String, Object>) idRequestDto.getRequest().getIdentity()).put("UIN", uin);
            }
        } catch (Exception e) {
            logger.error(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.APPLICATIONID.toString(), LoggerFileConstant.APPLICATIONID.toString(),
                    e.getMessage() + ExceptionUtils.getStackTrace(e));
        }
        logger.info("UIN Generation success.");
    }

}
