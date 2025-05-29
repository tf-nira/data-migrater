package io.mosip.packet.extractor.controller;

import io.mosip.kernel.core.logger.spi.Logger;

import io.mosip.packet.core.config.activity.Activity;
import io.mosip.packet.core.constant.activity.ActivityName;
import io.mosip.packet.core.constant.GlobalConfig;
import io.mosip.packet.core.dto.NINDetailsResponseDto;
import io.mosip.packet.core.dto.PacketResponseDto;
import io.mosip.packet.core.dto.RequestWrapper;
import io.mosip.packet.core.dto.ResponseWrapper;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.dto.dbimport.DBImportResponse;
import io.mosip.packet.core.dto.dbimport.PacketCreatorResponse;
import io.mosip.packet.core.dto.dbimport.PacketStatusRequest;
import io.mosip.packet.core.dto.packet.RegistrationIdRequest;
import io.mosip.packet.core.exception.ServiceError;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.util.RestApiClient;
import io.mosip.packet.extractor.service.DataExtractionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@RestController
@RequestMapping("/dataExtractor")
public class DataExtractionController {

    Logger LOGGER = DataProcessLogger.getLogger(DataExtractionController.class);

    @Autowired
    DataExtractionService dataExtractionService;

    @Autowired
    Activity activity;

    @PostMapping(value = "/extractImageFromPacket", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseWrapper> extractImageFromRID(@RequestBody RequestWrapper<RegistrationIdRequest> request) {
        ResponseWrapper<String> responseWrapper = new ResponseWrapper();
        try {
            RegistrationIdRequest ridRequest = request.getRequest();
            dataExtractionService.extractBioDataFromPacket(ridRequest);
            responseWrapper.setResponse("Successfully Completed");
        } catch (SQLException e) {
            e.printStackTrace();
            responseWrapper.setResponse("Error : " + e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            responseWrapper.setResponse("Error : " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            responseWrapper.setResponse("Error : " + e.getMessage());
        }
        return new ResponseEntity<ResponseWrapper>(responseWrapper, HttpStatus.OK);
    }

    @PostMapping(value = "/importDBBioDataToImg", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseWrapper> importBioImgFromDB(@RequestBody RequestWrapper<DBImportRequest> request) {
        ResponseWrapper<DBImportResponse> responseWrapper = new ResponseWrapper();
        DBImportResponse response = new DBImportResponse();
        try {
            DBImportRequest importRequest = request.getRequest();
            dataExtractionService.extractBioDataFromDB(importRequest, true);
            response.setMessage("Successfully Completed");
        } catch (SQLException e) {
            e.printStackTrace();
            response.setMessage("Error : " + e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            response.setMessage("Error : " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            response.setMessage("Error : " + e.getMessage());
        }
        responseWrapper.setResponse(response);
        return new ResponseEntity<ResponseWrapper>(responseWrapper, HttpStatus.OK);
    }

    @PostMapping(value = "/importDBBioDataToData", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseWrapper> importBioDataFromDB(@RequestBody RequestWrapper<DBImportRequest> request) {
        ResponseWrapper<DBImportResponse> responseWrapper = new ResponseWrapper();
        DBImportResponse response = new DBImportResponse();
        try {
            DBImportRequest importRequest = request.getRequest();
            response.setConvertedBioValues(dataExtractionService.extractBioDataFromDB(importRequest, false));
            response.setMessage("Successfully Completed");
        } catch (SQLException e) {
            e.printStackTrace();
            response.setMessage("Error : " + e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            response.setMessage("Error : " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            response.setMessage("Error : " + e.getMessage());
        }
        responseWrapper.setResponse(response);
        return new ResponseEntity<ResponseWrapper>(responseWrapper, HttpStatus.OK);
    }

    @PostMapping(value = "/importDBBioDataToByteData", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseWrapper> importBioByteDataFromDB(@RequestBody RequestWrapper<DBImportRequest> request) {
        ResponseWrapper<DBImportResponse> responseWrapper = new ResponseWrapper();
        DBImportResponse response = new DBImportResponse();
        responseWrapper.setErrors(new ArrayList<>());

        try {
            DBImportRequest importRequest = request.getRequest();
            response.setConvertedBioValues(dataExtractionService.extractBioDataFromDBAsBytes(importRequest, false));
            response.setMessage("Successfully Completed");
        } catch (SQLException e) {
            e.printStackTrace();
            response.setMessage("Error : " + e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            response.setMessage("Error : " + e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            response.setMessage("Error : " + e.getMessage());
        }
        responseWrapper.setResponse(response);
        return new ResponseEntity<ResponseWrapper>(responseWrapper, HttpStatus.OK);
    }

    @PostMapping(value = "/importPacketsFromOtherDomain", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseWrapper> createPacketFromOtherDomain(@RequestBody RequestWrapper<DBImportRequest> request) {
        ResponseWrapper<PacketCreatorResponse> responseWrapper = new ResponseWrapper();
        PacketCreatorResponse response = new PacketCreatorResponse();

        try {
            GlobalConfig.setActivity(activity.setActivity(ActivityName.DATA_CREATOR.name()));
            DBImportRequest importRequest = request.getRequest();
            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "DataExtractionController :: importPacketsFromOtherDomain():: entry");
            response = dataExtractionService.createPacketFromDataBase(importRequest);
            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "DataExtractionController :: importPacketsFromOtherDomain():: exit");
        } catch (SQLException e) {
            e.printStackTrace();
            ServiceError error = new ServiceError();
            error.setErrorCode("IX-0001");
            error.setMessage("Error : " + e.getMessage());
            responseWrapper.getErrors().add(error);
            LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, error.getErrorCode(), error.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            ServiceError error = new ServiceError();
            error.setErrorCode("IX-0001");
            error.setMessage("Error : " + e.getMessage());
            responseWrapper.getErrors().add(error);
            LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, error.getErrorCode(), error.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            ServiceError error = new ServiceError();
            error.setErrorCode("IX-0001");
            error.setMessage("Error : " + e.getMessage());
            responseWrapper.getErrors().add(error);
            LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, error.getErrorCode(), error.getMessage());
        }
        responseWrapper.setResponse(response);
        return new ResponseEntity<ResponseWrapper>(responseWrapper, HttpStatus.OK);
    }

    @PostMapping(value = "/exportBioQualityFromOtherDomain", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseWrapper> exportBioQualityFromOtherDomain(@RequestBody RequestWrapper<DBImportRequest> request) {
        RestApiClient.setIsAuthRequired(false);
        ResponseWrapper<PacketCreatorResponse> responseWrapper = new ResponseWrapper();
        PacketCreatorResponse response = new PacketCreatorResponse();
        try {
            GlobalConfig.setActivity(activity.setActivity(ActivityName.DATA_QUALITY_ANALYZER.name()));
            DBImportRequest importRequest = request.getRequest();
            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "DataExtractionController :: importPacketsFromOtherDomain():: entry");
            response = dataExtractionService.createPacketFromDataBase(importRequest);
            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "DataExtractionController :: importPacketsFromOtherDomain():: exit");
        } catch (SQLException e) {
            e.printStackTrace();
            ServiceError error = new ServiceError();
            error.setErrorCode("IX-0001");
            error.setMessage("Error : " + e.getMessage());
            responseWrapper.getErrors().add(error);
            LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, error.getErrorCode(), error.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
            ServiceError error = new ServiceError();
            error.setErrorCode("IX-0001");
            error.setMessage("Error : " + e.getMessage());
            responseWrapper.getErrors().add(error);
            LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, error.getErrorCode(), error.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            ServiceError error = new ServiceError();
            error.setErrorCode("IX-0001");
            error.setMessage("Error : " + e.getMessage());
            responseWrapper.getErrors().add(error);
            LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, error.getErrorCode(), error.getMessage());
        }
        responseWrapper.setResponse(response);
        RestApiClient.setIsAuthRequired(true);
        return new ResponseEntity<ResponseWrapper>(responseWrapper, HttpStatus.OK);
    }

    @GetMapping(value = "/refreshQualityAnalysisData", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseWrapper> refreshQualityAnalysisData() {
        ResponseWrapper<String> responseWrapper = new ResponseWrapper();
        try {
            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "DataExtractionController :: exportBioQualityFromOtherDomain():: entry");
            responseWrapper.setResponse(dataExtractionService.refreshQualityAnalysisData());
            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "DataExtractionController :: exportBioQualityFromOtherDomain():: exit");
        }  catch (Exception e) {
            e.printStackTrace();
            ServiceError error = new ServiceError();
            error.setErrorCode("IX-0001");
            error.setMessage("Error : " + e.getMessage());
            responseWrapper.getErrors().add(error);
            LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, error.getErrorCode(), error.getMessage());
        }
        return new ResponseEntity<ResponseWrapper>(responseWrapper, HttpStatus.OK);
    }

    @PostMapping(value = "/getPacketStatus", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseWrapper> getPacketStatus(@RequestBody RequestWrapper<PacketStatusRequest> request) {
        ResponseWrapper<PacketResponseDto> responseWrapper = new ResponseWrapper();
        try {
            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "DataExtractionController :: getPacketStatus():: entry");
            responseWrapper.setResponse(dataExtractionService.getPacketStatus(request.getRequest()));
            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "DataExtractionController :: getPacketStatus():: exit");
        }  catch (Exception e) {
            e.printStackTrace();
            ServiceError error = new ServiceError();
            error.setErrorCode("IX-0001");
            error.setMessage("Error : " + e.getMessage());
            responseWrapper.getErrors().add(error);
            LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, error.getErrorCode(), error.getMessage());
        }
        return new ResponseEntity<ResponseWrapper>(responseWrapper, HttpStatus.OK);
    }
    
    @PostMapping(value = "/getNINDetails", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseWrapper> getNINDetails(@RequestBody RequestWrapper<PacketStatusRequest> request) {
        ResponseWrapper<NINDetailsResponseDto> responseWrapper = new ResponseWrapper();
        try {
            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "DataExtractionController :: getNINDetails():: entry");
            responseWrapper.setResponse(dataExtractionService.getNINDetails(request.getRequest()));
            LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "DataExtractionController :: getNINDetails():: exit");
        }  catch (Exception e) {
            e.printStackTrace();
            ServiceError error = new ServiceError();
            error.setErrorCode("IX-0001");
            error.setMessage("Error : " + e.getMessage());
            if(e.getMessage().contains("Closed Connection")) {
            	try {
            		responseWrapper.setResponse(dataExtractionService.getNINDetails(request.getRequest()));
				} catch (Exception e1) {
					error.setErrorCode("IX-0001");
		            error.setMessage("Error : " + e.getMessage());
		            if(e.getMessage().contains("Closed Connection")) {
		            	try {
							responseWrapper.setResponse(dataExtractionService.getNINDetails(request.getRequest()));
						} catch (Exception e2) {
							error.setErrorCode("IX-0001");
				            error.setMessage("Error : " + e.getMessage());
							e2.printStackTrace();
						}
		            }
				}
            }
            responseWrapper.getErrors().add(error);
            LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, error.getErrorCode(), error.getMessage());
        }
        return new ResponseEntity<ResponseWrapper>(responseWrapper, HttpStatus.OK);
    }
}
