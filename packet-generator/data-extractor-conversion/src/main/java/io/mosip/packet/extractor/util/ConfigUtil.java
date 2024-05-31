package io.mosip.packet.extractor.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import io.mosip.kernel.clientcrypto.service.impl.ClientCryptoFacade;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.FileUtils;
import io.mosip.kernel.cryptomanager.util.CryptomanagerUtils;
import io.mosip.kernel.keymanagerservice.dto.KeyPairGenerateResponseDto;
import io.mosip.kernel.keymanagerservice.dto.UploadCertificateRequestDto;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import io.mosip.kernel.keymanagerservice.util.KeymanagerUtil;
import io.mosip.packet.core.constant.ApiName;
import io.mosip.packet.core.constant.RegistrationConstants;
import io.mosip.packet.core.dto.RequestWrapper;
import io.mosip.packet.core.dto.ResponseWrapper;
import io.mosip.packet.core.dto.config.MosipMachineModel;
import io.mosip.packet.core.dto.config.SyncDataResponseDto;
import io.mosip.packet.core.dto.masterdata.*;
import io.mosip.packet.core.entity.MachineMaster;
import io.mosip.packet.core.exception.ApisResourceAccessException;
import io.mosip.packet.core.exception.ServiceError;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.repository.MachineMasterRepository;
import io.mosip.packet.core.service.DataRestClientService;
import io.mosip.packet.core.util.DateUtils;
import io.mosip.packet.core.util.RestApiClient;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.security.NoSuchAlgorithmException;
import java.util.*;

import static io.mosip.packet.core.constant.GlobalConfig.*;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
@Getter
public class ConfigUtil {

    private String keyIndex;
    private String machineName;
    private String machineId;
    private String centerId;
    private String regClientVersion;
    private String selectedLanguages;
    private String machineSerialNum;
    private static final Logger LOGGER = DataProcessLogger.getLogger(ConfigUtil.class);

    private ConfigUtil() {
    }

    @Autowired
    private Environment env;

    @Autowired
    private ClientCryptoFacade clientCryptoFacade;

    @Autowired
    private DataRestClientService restApiClient;

    @Autowired
    private ClientSettingSyncHelper clientSettingSyncHelper;

    @Autowired
    private MachineMasterRepository machineMasterRepository;

    @Autowired
    private KeymanagerService keymanagerService;

    @Autowired
    private CryptomanagerUtils cryptomanagerUtils;

    @Autowired
    private KeymanagerUtil keymanagerUtil;


    private static ConfigUtil configUtil;

    public void loadConfigDetails() throws Exception {
        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Loading Configuration");
        if (configUtil == null) {
            synchronized (ConfigUtil.class) {
                configUtil = new ConfigUtil();
                configUtil.machineName = InetAddress.getLocalHost().getHostName().toLowerCase();
                configUtil.regClientVersion = env.getProperty("mosip.id.regclient.current.version");
                configUtil.selectedLanguages = env.getProperty("mosip.selected.languages");
                IS_TPM_AVAILABLE = clientCryptoFacade.getClientSecurity().isTPMInstance();

                if(!IS_TPM_AVAILABLE) {
                    LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "TPM Not Available. Creating Key Pair for Machine " + configUtil.machineName);
                    System.out.println("TPM Not Available. Creating Key Pair for Machine " + configUtil.machineName);

                    String machineName = configUtil.machineName;

                    if (machineName == null || machineName.isEmpty())
                        throw new RuntimeException("MachineName is null or empty!");

                    List<HashMap<String, Object>> machines = null;
                    RequestWrapper wrapper = prepareMachineSearchDto(machineName);
                    ResponseWrapper responseWrapper = (ResponseWrapper<PageDto>) restApiClient.postApi(ApiName.MASTER_MACHINE_SEARCH,null, null, wrapper, ResponseWrapper.class, MediaType.APPLICATION_JSON);

                    if(responseWrapper.getErrors() != null && responseWrapper.getErrors().size() > 0) {
                        String message = getErrorMessage(getErrorList(responseWrapper));
                        throw new Exception("Error During Machine Fetch " + message);
                    }

                    if(responseWrapper.getResponse() != null) {
                        HashMap<String, Object> response = (HashMap<String, Object>) responseWrapper.getResponse();
                        machines = (List<HashMap<String, Object>>) response.get("data");

                        if(machines == null) {
                            createMachine(machineName);
                        } else {
                            for(HashMap<String, Object> map : machines) {
                                if(map.get("isActive").equals(true)) {
                                    machineId = (String) map.get("id");
                                    String publicKey = (String) map.get("publicKey");
                                    configUtil.keyIndex = CryptoUtil.computeFingerPrint(CryptoUtil.decodeURLSafeBase64(publicKey),null);
                                    break;
                                }
                            }
                        }
                    }
                } else {
                    configUtil.keyIndex = CryptoUtil.computeFingerPrint(clientCryptoFacade.getClientSecurity().getEncryptionPublicPart(), null);
                }

                syncClientSettings();
                if(!IS_NETWORK_AVAILABLE) {
                    System.out.println("Nerwork Not available for Host : " + env.getProperty("mosip.internal.host") + "  Do you want to Continue (Y-Yes, N-No)");
                    String option = "Y";

                    if(!IS_RUNNING_AS_BATCH) {
                        Scanner scanner = new Scanner(System.in);
                        option = scanner.next();
                    } else {
                        option ="Y";
                    }

                    if(option.equalsIgnoreCase("n")) {
                        System.exit(1);
                    }
                }
            }
        }
    }

    private void copyKeyFile(String sourceFile, String destinationFile) throws io.mosip.kernel.core.exception.IOException {
        File sourceKeyFile = new File(sourceFile);
        File destinationKeyFile = new File(destinationFile);
        FileUtils.copyFile(sourceKeyFile, destinationKeyFile);
    }

    private void createMachine(String machineName) throws NoSuchAlgorithmException, ApisResourceAccessException, IOException {
        String machineSpecId = env.getProperty("mosip.master.machine.spec.id");
        String regCenterId = env.getProperty("mosip.master.machine.reg.center.id");
        String zoneCode = env.getProperty("mosip.master.machine.zone.code");

        if(machineSpecId == null || machineSpecId.isEmpty())
            throw new RuntimeException("Machine Specification Id (MachineSpecId) is null or empty!");

        if(regCenterId == null || regCenterId.isEmpty())
            throw new RuntimeException("Registration Center Id (RegCenterId) is null or empty!");

        if(zoneCode == null || zoneCode.isEmpty())
            throw new RuntimeException("Zone Code (ZoneCode) is null or empty!");

        File publicKeyFile = new File(System.getProperty("user.dir") + File.separator + ".mosipkeys" + File.separator + "reg.pub");
        FileInputStream io = new FileInputStream(publicKeyFile);
        final String publicKey = java.util.Base64.getEncoder().encodeToString(io.readAllBytes());
        configUtil.keyIndex = CryptoUtil.computeFingerPrint(java.util.Base64.getDecoder().decode(publicKey),null);

        MosipMachineModel model = new MosipMachineModel();
        model.setLangCode(env.getProperty("mosip.selected.languages"));
        model.setMachineSpecId(machineSpecId);
        model.setName(machineName);
        model.setPublicKey(publicKey);
        model.setRegCenterId(regCenterId);
        model.setSignPublicKey(publicKey);
        model.setIsActive(true);
        model.setIpAddress("192.168.0.0");
        model.setValidityDateTime(DateUtils.formatToISOString(DateUtils.getUTCCurrentDateTime().plusYears(5)));
        model.setZoneCode(zoneCode);

        RequestWrapper machineRequestWrapper = prepareMachineCreateDto(model);;
        ResponseWrapper machineResponseWrapper = (ResponseWrapper) restApiClient.postApi(ApiName.MASTER_MACHINE_CREATE,null, null, machineRequestWrapper, ResponseWrapper.class, MediaType.APPLICATION_JSON);

        HashMap<String, Object> createResponse = (HashMap<String, Object>) machineResponseWrapper.getResponse();
        String queryParm = "id";
        String queryValue = (String) createResponse.get("id");
        ResponseWrapper machineUpdateResponseWrapper = (ResponseWrapper) restApiClient.patchApi(ApiName.MASTER_MACHINE_ACTIVATE,null, queryParm, queryValue, null, ResponseWrapper.class);

        if(machineUpdateResponseWrapper.getErrors() != null && !machineUpdateResponseWrapper.getErrors().isEmpty()) {
            String errorMessage = null;
            for(Object machineError : machineUpdateResponseWrapper.getErrors()) {
                ServiceError error = (ServiceError) machineError;

                if(errorMessage==null)
                    errorMessage = error.getErrorCode() + " - " + error.getMessage() + "\n";
                else
                    errorMessage += error.getErrorCode() + " - " + error.getMessage() + "\n";
            }
            throw new RuntimeException("Error While Creating Machine Error : " + errorMessage);
        }

        if(machineResponseWrapper.getErrors() == null || machineResponseWrapper.getErrors().isEmpty()) {
            HashMap<String, Object> machineResponse = (HashMap<String, Object>) machineResponseWrapper.getResponse();
            machineId = (String) machineResponse.get("id");

    //        createKeyFile(System.getProperty("user.dir") + File.separator + "privatekeys" + File.separator +
    //                machineId + ".reg.key", keyPair.getPrivate().getEncoded());
        } else {
            String errorMessage = null;
            for(Object machineError : machineResponseWrapper.getErrors()) {
                ServiceError error = (ServiceError) machineError;

                if(errorMessage==null)
                    errorMessage = error.getErrorCode() + " - " + error.getMessage() + "\n";
                else
                    errorMessage += error.getErrorCode() + " - " + error.getMessage() + "\n";
            }
            throw new RuntimeException("Error While Creating Machine Error : " + errorMessage);

        }
    }

    private RequestWrapper prepareMachineSearchDto(String machineId) {
        MachineSearchDto machineSearchDto = new MachineSearchDto();
        List<MachineFilter> filterList = new ArrayList<>();
        List<MachineSort> sortList = new ArrayList<>();

        MachineFilter machineFilter = new MachineFilter();
        MachineSort machineSort = new MachineSort();
        Pagination pagination = new Pagination();

        machineFilter.setType("equals");
        machineFilter.setColumnName("name");
        machineFilter.setValue(machineId);
        filterList.add(machineFilter);

        machineSort.setSortType("DESC");
        machineSort.setSortField("id");
        sortList.add(machineSort);

        pagination.setPageStart(0);
        pagination.setPageFetch(11);

        machineSearchDto.setFilters(filterList);
        machineSearchDto.setSort(sortList);
        machineSearchDto.setPagination(pagination);
        machineSearchDto.setLanguageCode(env.getProperty("mosip.selected.languages"));

        RequestWrapper<MachineSearchDto> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequest(machineSearchDto);
        requestWrapper.setRequesttime(DateUtils.formatToISOString(DateUtils.getUTCCurrentDateTime()));
        requestWrapper.setId("String");
        requestWrapper.setVersion("string");
        return requestWrapper;
    }

    private RequestWrapper prepareMachineCreateDto(MosipMachineModel machine) {
        RequestWrapper<MosipMachineModel> requestWrapper = new RequestWrapper<>();
        requestWrapper.setRequest(machine);
        requestWrapper.setRequesttime(DateUtils.formatToISOString(DateUtils.getUTCCurrentDateTime()));
        requestWrapper.setId("String");
        requestWrapper.setVersion("string");
        return requestWrapper;
    }

    public static ConfigUtil getConfigUtil() {
        return configUtil;
    }

    @SuppressWarnings("unchecked")
    private void syncClientSettings() throws Exception {
        try {
            ResponseWrapper response=null;
            try {
                response = (ResponseWrapper) restApiClient.getApi(ApiName.MASTER_VALIDATOR_SERVICE_NAME, null, "keyindex", configUtil.keyIndex, ResponseWrapper.class);
            } catch (Exception e) {
                e.printStackTrace();
                IS_NETWORK_AVAILABLE = false;
            }

            String message = getErrorMessage(getErrorList(response));

            if (null != response.getResponse()) {
                saveClientSettings((HashMap<String, Object>)response.getResponse());
            } else {
                throw new Exception("Machine Not Configured in MOSIP " + message);
            }

            List<MachineMaster> machineMasters = machineMasterRepository.findAll();

            if (machineMasters.size() > 0) {
                configUtil.machineSerialNum = machineMasters.get(0).getSerialNum();
                configUtil.machineId = machineMasters.get(0).getId();
                configUtil.centerId = machineMasters.get(0).getRegCenterId();
            } else {
                throw new Exception("Machine Details Fetching from MOSIP Failed");
            }

            if (!configUtil.machineName.equalsIgnoreCase(machineMasters.get(0).getName()))
                throw new Exception("Machine Name '" + configUtil.machineName + "' not Matching with the MOSIP Configuration");

            if (configUtil.machineId == null || configUtil.machineId.isEmpty())
                throw new Exception("Machine Name '" + configUtil.machineName + "' not Configured in MOSIP System");

            if (configUtil.centerId == null || configUtil.centerId.isEmpty())
                throw new Exception("Registration Center not Configured for Machine Name '" + configUtil.machineName + "' in MOSIP System");

            fetchPolicy();
        } catch (Exception e) {
            e.printStackTrace();
            if(!IS_ONLY_FOR_QUALITY_CHECK)
                throw e;
        }
    }

    private void saveClientSettings(HashMap<String, Object> masterSyncResponse) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String jsonString = mapper.writeValueAsString(masterSyncResponse);
        SyncDataResponseDto syncDataResponseDto = mapper.readValue(jsonString,
                new TypeReference<SyncDataResponseDto>() {
                });

        String response = null;
        try {
            response = clientSettingSyncHelper.saveClientSettings(syncDataResponseDto);
        } catch (Throwable runtimeException) {
            throw new Exception(runtimeException.getMessage());
        }
    }

    private static String getErrorMessage(List<Object> errorList) {

        return errorList != null && errorList.get(0) != null
                ? (String) ((ServiceError)errorList.get(0)).getErrorCode() + " : " + ((ServiceError)errorList.get(0)).getMessage()
                : null;
    }

    private static List<Object> getErrorList(ResponseWrapper syncReponse) {

        return syncReponse.getErrors() != null && syncReponse.getErrors().size() > 0
                ? (List<Object>) syncReponse.getErrors()
                : null;
    }

    public void fetchPolicy() throws Exception {
//        if (!serviceDelegateUtil.isNetworkAvailable()) {
 //           return setErrorResponse(responseDTO, RegistrationConstants.NO_INTERNET, null);
//        }
//TODO Need to Implement System online or not before starting process

        String stationId = configUtil.machineId;
        String centerId = stationId != null ? configUtil.centerId : null;
        validate(centerId, stationId);
        String centerMachineId = centerId.concat(RegistrationConstants.UNDER_SCORE).concat(stationId);

        String certificateData = getCertificateFromServer(centerMachineId); //fetch policy key from server
        KeyPairGenerateResponseDto certificateDto = getKeyFromLocalDB(centerMachineId); //get policy key from DB
        //compare downloaded and saved one, if different then save it
        if(certificateDto == null || !Arrays.equals(cryptomanagerUtils.getCertificateThumbprint(keymanagerUtil.convertToCertificate(certificateData)),
                cryptomanagerUtils.getCertificateThumbprint(keymanagerUtil.convertToCertificate(certificateDto.getCertificate())))) {
            UploadCertificateRequestDto uploadCertRequestDto = new UploadCertificateRequestDto();
            uploadCertRequestDto.setApplicationId(RegistrationConstants.REG_APP_ID);
            uploadCertRequestDto.setCertificateData(certificateData);
            uploadCertRequestDto.setReferenceId(centerMachineId);
            keymanagerService.uploadOtherDomainCertificate(uploadCertRequestDto);
        }
    }

    private boolean validate(String centerId, String machineId)
            throws Exception {
        if (centerId == null || machineId == null)
                throw new Exception("Machine ID & Center ID are Empty");

        return true;
    }

    private String getCertificateFromServer(String centerMachineId) throws Exception {
        List<String> queryParm = new ArrayList<>();
        queryParm.add(RegistrationConstants.GET_CERT_APP_ID);
        queryParm.add(RegistrationConstants.REF_ID);

        List<String> queryParmValue = new ArrayList<>();
        queryParmValue.add(RegistrationConstants.REG_APP_ID);
        queryParmValue.add(centerMachineId);

        ResponseWrapper responseWrapper = (ResponseWrapper) restApiClient
                .getApi(ApiName.GET_CERTIFICATE, null, queryParm, queryParmValue, ResponseWrapper.class);

        if(null != responseWrapper.getResponse()) {
            HashMap<String, Object> responseMap = (HashMap<String, Object>) responseWrapper.getResponse();
            return responseMap.get(RegistrationConstants.CERTIFICATE).toString();
        }

        if(responseWrapper.getErrors() != null &&
                responseWrapper.getErrors().size() > 0 ) {
            throw new Exception("Error : " + responseWrapper.getErrors());
//            LOGGER.error("Get Policy key from server failed with error {}", publicKeySyncResponse.get(RegistrationConstants.ERRORS));
        }

        throw new Exception("Failed to Policy Sync");
    }

    private KeyPairGenerateResponseDto getKeyFromLocalDB(String refId) {
        try {
            KeyPairGenerateResponseDto certificateDto = keymanagerService
                    .getCertificate(RegistrationConstants.REG_APP_ID, Optional.of(refId));

            if(certificateDto != null && certificateDto.getCertificate() != null)
                return certificateDto;

        } catch (Exception ex) {
 //           LOGGER.error("Error Fetching policy key from DB", ex);
        }
        return null;
    }

    private static void createKeyFile(final String fileName, final byte[] key) {
    //    logger.info("Creating file : " + fileName);
        File privateKeyFile = new File(fileName);
        File folderDirectory = new File(privateKeyFile.getParent());
        if(!folderDirectory.exists())
            folderDirectory.mkdirs();

        try (final FileOutputStream os = new FileOutputStream(privateKeyFile);) {
            Throwable t = null;
            try {

                try {
                    os.write(key);
                } finally {
                    if (os != null) {
                        os.close();
                    }
                }
            } finally {
                final Throwable exception = null;
                t = exception;
            }
        } catch (IOException e) {
            e.printStackTrace();
            // logger.error(e.getMessage());
        }
    }

}
