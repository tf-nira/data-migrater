package io.mosip.packet.core.service.impl;

import io.mosip.kernel.clientcrypto.service.impl.ClientCryptoFacade;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.JsonUtils;
import io.mosip.kernel.core.util.exception.JsonProcessingException;
import io.mosip.packet.core.constant.ApiName;
import io.mosip.packet.core.constant.RegistrationConstants;
import io.mosip.packet.core.exception.ApisResourceAccessException;
import io.mosip.packet.core.exception.PlatformErrorMessages;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.service.DataRestClientService;
import io.mosip.packet.core.util.RestApiClient;
import io.mosip.packet.core.util.regclient.RequestHTTPDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The Class RegistrationProcessorRestClientServiceImpl.
 * 
 * @author Rishabh Keshari
 */
@Service
public class DataRestClientServiceImpl implements DataRestClientService<Object> {

	/** The logger. */
	Logger LOGGER = DataProcessLogger.getLogger(DataRestClientServiceImpl.class);

	/** The rest api client. */
	@Autowired
	private RestApiClient restApiClient;

	/** The env. */
	@Autowired
	private Environment env;

	@Autowired
	private ClientCryptoFacade clientCryptoFacade;

	private static final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.mosip.registration.processor.core.spi.restclient.
	 * RegistrationProcessorRestClientService#getApi(io.mosip.registration.
	 * processor .core.code.ApiName,
	 * io.mosip.registration.processor.core.code.RestUriConstant, java.lang.String,
	 * java.lang.String, java.lang.Class)
	 */
	@Override
	public Object getApi(ApiName apiName, List<String> pathsegments, String queryParamName, String queryParamValue,
						 Class<?> responseType) throws ApisResourceAccessException {
		return getApi(apiName, pathsegments,queryParamName,  queryParamValue, responseType, true);
	}

	public Object getApi(ApiName apiName, List<String> pathsegments, String queryParamName, String queryParamValue,
						 Class<?> responseType, boolean isAuthRequired) throws ApisResourceAccessException {
		LOGGER.debug("RegistrationProcessorRestClientServiceImpl::getApi()::entry");
		RestApiClient.setIsAuthRequired(isAuthRequired);
		Object obj = null;
		String apiHostIpPort = env.getProperty(apiName.name());

		UriComponentsBuilder builder = null;
		UriComponents uriComponents = null;
		if (apiHostIpPort != null) {

			builder = UriComponentsBuilder.fromUriString(apiHostIpPort);
			if (!((pathsegments == null) || (pathsegments.isEmpty()))) {
				for (String segment : pathsegments) {
					if (!((segment == null) || (("").equals(segment)))) {
						builder.pathSegment(segment);
					}
				}

			}

			if (!((queryParamName == null) || (("").equals(queryParamName)))) {

				String[] queryParamNameArr = queryParamName.split(",");
				String[] queryParamValueArr = queryParamValue.split(",");
				for (int i = 0; i < queryParamNameArr.length; i++) {
					builder.queryParam(queryParamNameArr[i], queryParamValueArr[i]);
				}

			}

			try {

				uriComponents = builder.build(false).encode();
				LOGGER.debug(uriComponents.toUri().toString(), "URI");
				obj = restApiClient.getApi(uriComponents.toUri(), responseType, apiName.getLoginType());

			} catch (Exception e) {
				LOGGER.error(e.getMessage() , e);
				throw new ApisResourceAccessException(
						PlatformErrorMessages.PRT_RCT_UNKNOWN_RESOURCE_EXCEPTION.getCode(), e.getMessage(), e);
			}
		}
		LOGGER.debug("RegistrationProcessorRestClientServiceImpl::getApi()::exit");
		return obj;
	}

	@Override
	public Object getApi(ApiName apiName, List<String> pathsegments, List<String> queryParamName, List<Object> queryParamValue,
						 Class<?> responseType) throws ApisResourceAccessException {
		return getApi(apiName,pathsegments, queryParamName, queryParamValue, responseType, true );
	}

	public Object getApi(ApiName apiName, List<String> pathsegments, List<String> queryParamName, List<Object> queryParamValue,
						 Class<?> responseType, boolean isAuthRequired) throws ApisResourceAccessException {
		LOGGER.debug("RegistrationProcessorRestClientServiceImpl::getApi()::entry");
		RestApiClient.setIsAuthRequired(isAuthRequired);
		Object obj = null;
		String apiHostIpPort = env.getProperty(apiName.name());

		UriComponentsBuilder builder = null;
		UriComponents uriComponents = null;
		if (apiHostIpPort != null) {

			builder = UriComponentsBuilder.fromUriString(apiHostIpPort);
			if (!((pathsegments == null) || (pathsegments.isEmpty()))) {
				for (String segment : pathsegments) {
					if (!((segment == null) || (("").equals(segment)))) {
						builder.pathSegment(segment);
					}
				}

			}

			if (((queryParamName != null) && (!queryParamName.isEmpty()))) {
				for (int i = 0; i < queryParamName.size(); i++) {
					builder.queryParam(queryParamName.get(i), queryParamValue.get(i));
				}

			}

			try {

				uriComponents = builder.build(false).encode();
				LOGGER.debug(uriComponents.toUri().toString(),"URI");
				obj = restApiClient.getApi(uriComponents.toUri(), responseType, apiName.getLoginType());

			} catch (Exception e) {
				LOGGER.error(e.getMessage() , e);

				throw new ApisResourceAccessException(
						PlatformErrorMessages.PRT_RCT_UNKNOWN_RESOURCE_EXCEPTION.getCode(), e.getMessage(), e);

			}
		}
		LOGGER.debug("RegistrationProcessorRestClientServiceImpl::getApi()::exit");
		return obj;
	}

	public Object postApi(ApiName apiName, String queryParamName, String queryParamValue, Object requestedData,
						  Class<?> responseType, MediaType mediaType) throws ApisResourceAccessException {
		return postApi(apiName, queryParamName, queryParamValue,  requestedData, responseType, mediaType, true);
	}
	public Object postApi(ApiName apiName, String queryParamName, String queryParamValue, Object requestedData,
			Class<?> responseType, MediaType mediaType, boolean isAuthRequired) throws ApisResourceAccessException {
		LOGGER.debug("RegistrationProcessorRestClientServiceImpl::postApi()::entry");

		RestApiClient.setIsAuthRequired(isAuthRequired);
		Object obj = null;
		String apiHostIpPort = env.getProperty(apiName.name());
		UriComponentsBuilder builder = null;
		if (apiHostIpPort != null)
			builder = UriComponentsBuilder.fromUriString(apiHostIpPort);
		if (builder != null) {

			if (!((queryParamName == null) || (("").equals(queryParamName)))) {
				String[] queryParamNameArr = queryParamName.split(",");
				String[] queryParamValueArr = queryParamValue.split(",");

				for (int i = 0; i < queryParamNameArr.length; i++) {
					builder.queryParam(queryParamNameArr[i], queryParamValueArr[i]);
				}
			}

			try {
				obj = restApiClient.postApi(builder.toUriString(), mediaType, requestedData, responseType, apiName.getLoginType());

			} catch (Exception e) {
				LOGGER.error(e.getMessage() , e);

				throw new ApisResourceAccessException(PlatformErrorMessages.PRT_RCT_UNKNOWN_RESOURCE_EXCEPTION.getCode(),
						e.getMessage(), e);

			}
		}
		LOGGER.debug("RegistrationProcessorRestClientServiceImpl::postApi()::exit");
		return obj;
	}

	public Object postApi(String apiHostIpPort, String queryParamName, String queryParamValue, Object requestedData,
						  Class<?> responseType, MediaType mediaType, boolean isAuthRequired, ApiName apiName) throws ApisResourceAccessException {
		LOGGER.debug("RegistrationProcessorRestClientServiceImpl::postApi()::entry");
		RestApiClient.setIsAuthRequired(isAuthRequired);
		Object obj = null;
		UriComponentsBuilder builder = null;
		if (apiHostIpPort != null)
			builder = UriComponentsBuilder.fromUriString(apiHostIpPort);
		if (builder != null) {

			if (!((queryParamName == null) || (("").equals(queryParamName)))) {
				String[] queryParamNameArr = queryParamName.split(",");
				String[] queryParamValueArr = queryParamValue.split(",");

				for (int i = 0; i < queryParamNameArr.length; i++) {
					builder.queryParam(queryParamNameArr[i], queryParamValueArr[i]);
				}
			}

			try {
				obj = restApiClient.postApi(builder.toUriString(), mediaType, requestedData, responseType, apiName.getLoginType());

			} catch (Exception e) {
				LOGGER.error(e.getMessage() , e);

				throw new ApisResourceAccessException(PlatformErrorMessages.PRT_RCT_UNKNOWN_RESOURCE_EXCEPTION.getCode(),
						e.getMessage(), e);

			}
		}
		LOGGER.debug("RegistrationProcessorRestClientServiceImpl::postApi()::exit");
		return obj;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.mosip.registration.processor.core.spi.restclient.
	 * RegistrationProcessorRestClientService#postApi(io.mosip.registration.
	 * processor.core.code.ApiName,
	 * io.mosip.registration.processor.core.code.RestUriConstant, java.lang.String,
	 * java.lang.String, java.lang.Object, java.lang.Class)
	 */
	@Override
	public Object postApi(ApiName apiName, String queryParamName, String queryParamValue, Object requestedData,
			Class<?> responseType) throws ApisResourceAccessException {
		return postApi(apiName, queryParamName, queryParamValue, requestedData, responseType, null);
	}

	@Override
	public Object postApi(ApiName apiName, String queryParamName, String queryParamValue, Object requestedData,
						  Class<?> responseType, boolean isAuthRequired) throws ApisResourceAccessException {
		return postApi(apiName, queryParamName, queryParamValue, requestedData, responseType, null, isAuthRequired);
	}

	@Override
	public Object postApi(String apiHostIpPort, String queryParam, String queryParamValue, Object requestedData, Class<?> responseType, boolean isAuthRequired, ApiName apiName) throws ApisResourceAccessException {
		return postApi(apiHostIpPort, queryParam, queryParamValue, requestedData, responseType, null, isAuthRequired, apiName);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see io.mosip.registration.processor.core.spi.restclient.
	 * RegistrationProcessorRestClientService#postApi(io.mosip.registration.
	 * processor.core.code.ApiName, java.util.List, java.lang.String,
	 * java.lang.String, java.lang.Object, java.lang.Class)
	 */
	@Override
	public Object postApi(ApiName apiName, List<String> pathsegments, String queryParamName, String queryParamValue,
						  Object requestedData, Class<?> responseType) throws ApisResourceAccessException {
		return postApi(apiName, pathsegments, queryParamName, queryParamValue, requestedData, responseType, true);
	}

	public Object postApi(ApiName apiName, List<String> pathsegments, String queryParamName, String queryParamValue,
			Object requestedData, Class<?> responseType, boolean isAuthRequired) throws ApisResourceAccessException {

		LOGGER.debug("RegistrationProcessorRestClientServiceImpl::postApi()::entry");
		RestApiClient.setIsAuthRequired(isAuthRequired);
		Object obj = null;
		String apiHostIpPort = env.getProperty(apiName.name());
		UriComponentsBuilder builder = null;
		if (apiHostIpPort != null)
			builder = UriComponentsBuilder.fromUriString(apiHostIpPort);
		if (builder != null) {

			if (!((pathsegments == null) || (pathsegments.isEmpty()))) {
				for (String segment : pathsegments) {
					if (!((segment == null) || (("").equals(segment)))) {
						builder.pathSegment(segment);
					}
				}

			}
			if (!checkNull(queryParamName)) {
				String[] queryParamNameArr = queryParamName.split(",");
				String[] queryParamValueArr = queryParamValue.split(",");

				for (int i = 0; i < queryParamNameArr.length; i++) {
					builder.queryParam(queryParamNameArr[i], queryParamValueArr[i]);
				}
			}

			try {
				obj = restApiClient.postApi(builder.toUriString(), null, requestedData, responseType, apiName.getLoginType());

			} catch (Exception e) {
				LOGGER.error(e.getMessage() , e);

				throw new ApisResourceAccessException(PlatformErrorMessages.PRT_RCT_UNKNOWN_RESOURCE_EXCEPTION.getCode(),
						e.getMessage(), e);

			}
		}
		LOGGER.debug("RegistrationProcessorRestClientServiceImpl::postApi()::exit");
		return obj;
	}

	@Override
	public Object postApi(ApiName apiName, MediaType mediaType, List<String> pathsegments, List<String> queryParamName, List<Object> queryParamValue,
						  Object requestedData, Class<?> responseType) throws ApisResourceAccessException {
		return postApi(apiName, mediaType, pathsegments, queryParamName, queryParamValue, requestedData, responseType, true);
	}

	public Object postApi(ApiName apiName, MediaType mediaType, List<String> pathsegments, List<String> queryParamName, List<Object> queryParamValue,
						  Object requestedData, Class<?> responseType, boolean isAuthRequired) throws ApisResourceAccessException {

		LOGGER.debug("RegistrationProcessorRestClientServiceImpl::postApi()::entry");
		RestApiClient.setIsAuthRequired(isAuthRequired);
		Object obj = null;
		String apiHostIpPort = env.getProperty(apiName.name());
		UriComponentsBuilder builder = null;
		if (apiHostIpPort != null)
			builder = UriComponentsBuilder.fromUriString(apiHostIpPort);
		if (builder != null) {

			if (!((pathsegments == null) || (pathsegments.isEmpty()))) {
				for (String segment : pathsegments) {
					if (!((segment == null) || (("").equals(segment)))) {
						builder.pathSegment(segment);
					}
				}

			}
			if (!CollectionUtils.isEmpty(queryParamName)) {

				for (int i = 0; i < queryParamName.size(); i++) {
					builder.queryParam(queryParamName.get(i), queryParamValue.get(i));
				}
			}

			try {
				obj = restApiClient.postApi(builder.toUriString(), mediaType, requestedData, responseType, apiName.getLoginType());

			} catch (Exception e) {
				LOGGER.error(e.getMessage() , e);
				throw new ApisResourceAccessException(PlatformErrorMessages.PRT_RCT_UNKNOWN_RESOURCE_EXCEPTION.getCode(),
						e.getMessage(), e);

			}
		}
		LOGGER.debug("RegistrationProcessorRestClientServiceImpl::postApi()::exit");
		return obj;
	}



	/**
	 * Check null.
	 *
	 * @param queryParamName
	 *            the query param name
	 * @return true, if successful
	 */
	private boolean checkNull(String queryParamName) {

		return ((queryParamName == null) || (("").equals(queryParamName)));
	}

	/**
	 * Access resource using restTemplate {@link RestTemplate}
	 * Note: restTemplate is synchronous client
	 * @param requestHTTPDTO
	 * @return
	 * @throws RestClientException
	 */
	public Map<String, Object> invokeURL(RequestHTTPDTO requestHTTPDTO) throws Exception {
		Map<String, Object> responseMap = null;

		addAuthZToken(requestHTTPDTO);
		HashMap responseEntity = restApiClient.invoke(requestHTTPDTO.getUri(), requestHTTPDTO.getHttpMethod(),
				requestHTTPDTO.getHttpEntity(), requestHTTPDTO.getClazz(), getHttpRequestFactory());

		if (responseEntity != null) {
			responseMap = new HashMap<>();
			responseMap.put(RegistrationConstants.REST_RESPONSE_BODY, responseEntity.get("response"));
			responseMap.put(RegistrationConstants.ERROR, responseEntity.get("errors"));
		}

		return responseMap;
	}

	public SimpleClientHttpRequestFactory getHttpRequestFactory() {
		requestFactory.setReadTimeout(
				Integer.parseInt((String) env.getProperty(RegistrationConstants.HTTP_API_READ_TIMEOUT)));
		requestFactory.setConnectTimeout(
				Integer.parseInt((String) env.getProperty(RegistrationConstants.HTTP_API_WRITE_TIMEOUT)));
		return requestFactory;
	}

	public void addAuthZToken(RequestHTTPDTO requestHTTPDTO) throws Exception {
		try {
			LOGGER.info("Auth advice triggered to check add authZ token to web service request header...");

			if (requestHTTPDTO.isRequestSignRequired()) {
				addRequestSignature(requestHTTPDTO.getHttpHeaders(), requestHTTPDTO.getRequestBody());
			}

			if (requestHTTPDTO.isAuthRequired()) {
				String authZToken = restApiClient.getToken();
				setAuthHeaders(requestHTTPDTO.getHttpHeaders(), requestHTTPDTO.getAuthZHeader(), authZToken);
			}

			requestHTTPDTO.setHttpEntity(new HttpEntity<>(requestHTTPDTO.getRequestBody(), requestHTTPDTO.getHttpHeaders()));

			LOGGER.info("completed with request Auth advice");
		} catch (Exception regBaseCheckedException) {
			LOGGER.error("Failed in AuthAdvice >> {} {}", requestHTTPDTO.getUri(), regBaseCheckedException);
			throw regBaseCheckedException;
		}
	}

	private void addRequestSignature(HttpHeaders httpHeaders, Object requestBody) throws Exception {
		LOGGER.info("Adding request signature to request header");

		try {
			httpHeaders.add("request-signature", String.format("Authorization:%s", CryptoUtil
					.encodeToURLSafeBase64(clientCryptoFacade.getClientSecurity().signData(JsonUtils.javaObjectToJsonString(requestBody).getBytes()))));
			httpHeaders.add(RegistrationConstants.KEY_INDEX, CryptoUtil.computeFingerPrint(
					clientCryptoFacade.getClientSecurity().getEncryptionPublicPart(), null));
		} catch (JsonProcessingException jsonProcessingException) {
			throw jsonProcessingException;
		}

		LOGGER.info("Completed adding request signature to request header completed");
	}

	private void setAuthHeaders(HttpHeaders httpHeaders, String authHeader, String authZCookie) {
		LOGGER.info("Adding authZ token to request header");

		String[] arrayAuthHeaders = null;

		if (authHeader != null) {
			arrayAuthHeaders = authHeader.split(":");
			if (arrayAuthHeaders[1].equalsIgnoreCase(RegistrationConstants.REST_OAUTH)) {
				httpHeaders.add(RegistrationConstants.COOKIE, authZCookie);
			} else if (arrayAuthHeaders[1].equalsIgnoreCase(RegistrationConstants.AUTH_TYPE)) {
				httpHeaders.add(arrayAuthHeaders[0], arrayAuthHeaders[1]);
			}
		}

		LOGGER.info("Adding of authZ token to request header completed");
	}

	public Object putApi(ApiName apiName, String queryParamName, String queryParamValue, Object requestedData,
						  Class<?> responseType, MediaType mediaType) throws ApisResourceAccessException {
		return putApi(apiName, queryParamName, queryParamValue,  requestedData, responseType, mediaType, true);
	}
	public Object putApi(ApiName apiName, String queryParamName, String queryParamValue, Object requestedData,
						  Class<?> responseType, MediaType mediaType, boolean isAuthRequired) throws ApisResourceAccessException {
		LOGGER.debug("RegistrationProcessorRestClientServiceImpl::putApi()::entry");

		RestApiClient.setIsAuthRequired(isAuthRequired);
		Object obj = null;
		String apiHostIpPort = env.getProperty(apiName.name());
		UriComponentsBuilder builder = null;
		if (apiHostIpPort != null)
			builder = UriComponentsBuilder.fromUriString(apiHostIpPort);
		if (builder != null) {

			if (!((queryParamName == null) || (("").equals(queryParamName)))) {
				String[] queryParamNameArr = queryParamName.split(",");
				String[] queryParamValueArr = queryParamValue.split(",");

				for (int i = 0; i < queryParamNameArr.length; i++) {
					builder.queryParam(queryParamNameArr[i], queryParamValueArr[i]);
				}
			}

			try {
				obj = restApiClient.putApi(builder.toUriString(), requestedData, responseType, mediaType, apiName.getLoginType());

			} catch (Exception e) {
				LOGGER.error(e.getMessage() , e);

				throw new ApisResourceAccessException(PlatformErrorMessages.PRT_RCT_UNKNOWN_RESOURCE_EXCEPTION.getCode(),
						e.getMessage(), e);

			}
		}
		LOGGER.debug("RegistrationProcessorRestClientServiceImpl::putApi()::exit");
		return obj;
	}

	public Object patchApi(ApiName apiName, List<String> pathsegments, String queryParamName, String queryParamValue,
						   Object requestedData, Class<?> responseType) throws ApisResourceAccessException {

		LOGGER.debug("RegistrationProcessorRestClientServiceImpl::putApi()::entry");
		Object obj = null;
		String apiHostIpPort = env.getProperty(apiName.name());
		UriComponentsBuilder builder = null;
		if (apiHostIpPort != null)
			builder = UriComponentsBuilder.fromUriString(apiHostIpPort);
		if (builder != null) {

			if (!((pathsegments == null) || (pathsegments.isEmpty()))) {
				for (String segment : pathsegments) {
					if (!((segment == null) || (("").equals(segment)))) {
						builder.pathSegment(segment);
					}
				}

			}
			if (!checkNull(queryParamName)) {
				String[] queryParamNameArr = queryParamName.split(",");
				String[] queryParamValueArr = queryParamValue.split(",");

				for (int i = 0; i < queryParamNameArr.length; i++) {
					builder.queryParam(queryParamNameArr[i], queryParamValueArr[i]);
				}
			}

			try {
				obj = restApiClient.patchApi(builder.toUriString(), requestedData, responseType, apiName.getLoginType());

			} catch (Exception e) {
				LOGGER.error(e.getMessage() , e);

				throw new ApisResourceAccessException(
						PlatformErrorMessages.PRT_RCT_UNKNOWN_RESOURCE_EXCEPTION.getMessage(), e);

			}
		}
		LOGGER.debug("RegistrationProcessorRestClientServiceImpl::putApi()::exit");
		return obj;
	}
}