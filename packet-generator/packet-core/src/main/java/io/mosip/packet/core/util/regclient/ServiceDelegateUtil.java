package io.mosip.packet.core.util.regclient;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Path;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;

import io.micrometer.core.annotation.Timed;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.packetuploader.exception.ConnectionException;
import io.mosip.packet.core.constant.RegistrationConstants;
import io.mosip.packet.core.dto.ResponseWrapper;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.service.DataRestClientService;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import io.mosip.kernel.core.util.DateUtils;

/**
 * This is a helper class .it invokes with different classes to get the response
 * 
 * @author Yaswanth S
 * @since 1.0.0
 *
 */
@Component("serviceDelegateUtil")
public class ServiceDelegateUtil {

	private static final Logger LOGGER = DataProcessLogger.getLogger(ServiceDelegateUtil.class);
	public static final String MOSIP_HOSTNAME_PLACEHOLDER = "${mosip.hostname}";

	@Autowired
	private DataRestClientService restClientUtil;

	@Autowired
	private Environment environment;

	private String cernterId;

	private String machineId;

	public void setCenterMachineId(String centerId, String machineId) {
		this.cernterId = centerId;
		this.machineId = machineId;
	}
	/**
	 * Prepare and trigger GET request.
	 *
	 * @param serviceName
	 *            service to be invoked
	 * @param requestParams
	 *            parameters along with url
	 * @param hasPathParams
	 *            the has path params
	 * @param triggerPoint
	 *            system or user driven invocation
	 * @return Object requiredType of object response Body
	 * @throws RegBaseCheckedException
	 *             generalised exception with errorCode and errorMessage
	 * @throws ConnectionException
	 *             when client error exception from server / server exception
	 */
	public Object get(String serviceName, Map<String, String> requestParams, boolean hasPathParams, String triggerPoint, String trackerRefId)
			throws Throwable {

		LOGGER.debug("Get method has been called - {}", serviceName);

		Map<String, Object> responseMap;
		Object responseBody = null;

		RequestHTTPDTO requestHTTPDTO = new RequestHTTPDTO();

		try {
			requestHTTPDTO = prepareGETRequest(requestHTTPDTO, serviceName, requestParams);
			requestHTTPDTO.setAuthRequired(
					Boolean.valueOf(getEnvironmentProperty(serviceName, RegistrationConstants.AUTH_REQUIRED)));
			requestHTTPDTO.setAuthZHeader(getEnvironmentProperty(serviceName, RegistrationConstants.AUTH_HEADER));
			requestHTTPDTO.setIsSignRequired(
					Boolean.valueOf(getEnvironmentProperty(serviceName, RegistrationConstants.SIGN_REQUIRED)));
			requestHTTPDTO.setTriggerPoint(triggerPoint);
			requestHTTPDTO.setRequestSignRequired(
					Boolean.valueOf(getEnvironmentProperty(serviceName, RegistrationConstants.REQUEST_SIGN_REQUIRED)));

			// URI creation
			String url = getEnvironmentProperty(serviceName, RegistrationConstants.SERVICE_URL);
			Map<String, String> queryParams = new HashMap<>();
			for (String key : requestParams.keySet()) {
				if (!url.contains("{" + key + "}")) {
					queryParams.put(key, requestParams.get(key));
				}
			}

			if (hasPathParams) {
				requestHTTPDTO.setUri(UriComponentsBuilder.fromUriString(url).build(requestParams));
				url = requestHTTPDTO.getUri().toString();
			}

			/** Set URI */
			setURI(requestHTTPDTO, queryParams, url);

			responseMap = restClientUtil.invokeURL(requestHTTPDTO, trackerRefId);


		}  catch (RestClientException e) {
			LOGGER.error(e.getMessage(), e);
			throw e;
		}

		if (isResponseValid(responseMap, RegistrationConstants.REST_RESPONSE_BODY)) {
			responseBody = responseMap.get(RegistrationConstants.REST_RESPONSE_BODY);
		}
		LOGGER.debug("Get method has been ended - {}", serviceName);

		return responseBody;
	}

	/**
	 * prepare and trigger POST request.
	 *
	 * @param serviceName
	 *            service to be invoked
	 * @param object
	 *            request type
	 * @param triggerPoint
	 *            system or user driven invocation
	 * @return Object requiredType of object response Body
	 * @throws RegBaseCheckedException
	 *             generalised exception with errorCode and errorMessage
	 * @throws ConnectionException
	 *             when client error, server error, access error
	 */
	public Object post(String serviceName, Object object, String triggerPoint, String trackerRefId) throws Exception {
		LOGGER.debug("Post method called - {} ", serviceName);

		RequestHTTPDTO requestDto;
		Map<String, Object> responseMap = null;

		try {
			requestDto = preparePOSTRequest(serviceName, object);
			requestDto.setAuthRequired(
					Boolean.valueOf(getEnvironmentProperty(serviceName, RegistrationConstants.AUTH_REQUIRED)));
			requestDto.setAuthZHeader(getEnvironmentProperty(serviceName, RegistrationConstants.AUTH_HEADER));
			requestDto.setIsSignRequired(
					Boolean.valueOf(getEnvironmentProperty(serviceName, RegistrationConstants.SIGN_REQUIRED)));
			requestDto.setTriggerPoint(triggerPoint);
			requestDto.setRequestSignRequired(
					Boolean.valueOf(getEnvironmentProperty(serviceName, RegistrationConstants.REQUEST_SIGN_REQUIRED)));
			responseMap = restClientUtil.invokeURL(requestDto, trackerRefId);
		} catch (RestClientException e) {
			LOGGER.error(e.getMessage(), e);
			throw e;
		}
		LOGGER.debug("Post method ended - {} ", serviceName);

		return responseMap;
	}


	/**
	 * Prepare GET request.
	 *
	 * @param requestHTTPDTO
	 *            the request HTTPDTO
	 * @param serviceName
	 *            service to be invoked
	 * @param requestParams
	 *            params need to add along with url
	 * @return RequestHTTPDTO requestHTTPDTO with required data
	 * @throws RegBaseCheckedException
	 *             the reg base checked exception
	 */
	private RequestHTTPDTO prepareGETRequest(RequestHTTPDTO requestHTTPDTO, final String serviceName,
			final Map<String, String> requestParams) throws ClassNotFoundException {
		LOGGER.debug("Prepare Get request method called");

		// prepare httpDTO except rquest type and uri build
		prepareRequest(requestHTTPDTO, serviceName, null);

		// ResponseType
		Class<?> responseClass = null;
		try {
			responseClass = Class.forName(getEnvironmentProperty(serviceName, RegistrationConstants.RESPONSE_TYPE));
		} catch (ClassNotFoundException classNotFoundException) {
			throw classNotFoundException;
		}

		requestHTTPDTO.setClazz(responseClass);

		LOGGER.debug("Prepare Get request method ended");

		return requestHTTPDTO;
	}

	/**
	 * Prepare POST request.
	 *
	 * @param serviceName
	 *            service to be invoked
	 * @param object
	 *            request type
	 * @return RequestHTTPDTO requestHTTPDTO with required data
	 */
	private RequestHTTPDTO preparePOSTRequest(final String serviceName, final Object object) {
		LOGGER.debug("Preparing post request for web-service");

		// DTO need to to be prepared
		RequestHTTPDTO requestHTTPDTO = new RequestHTTPDTO();

		// prepare httpDTO except rquest type and uri build
		prepareRequest(requestHTTPDTO, serviceName, object);

		// URI creation
		setURI(requestHTTPDTO, null, getEnvironmentProperty(serviceName, RegistrationConstants.SERVICE_URL));

		// RequestType
		requestHTTPDTO.setClazz(Object.class);

		LOGGER.debug("Completed preparing post request for web-service");

		return requestHTTPDTO;

	}

	/**
	 * Sets the URI.
	 *
	 * @param requestHTTPDTO
	 *            the request HTTPDTO
	 * @param requestParams
	 *            the request params
	 * @param url
	 *            the url
	 */
	private void setURI(RequestHTTPDTO requestHTTPDTO, Map<String, String> requestParams, String url) {
		LOGGER.debug("Preparing URI for web-service");

		// BuildURIComponent
		UriComponentsBuilder uriComponentsBuilder = UriComponentsBuilder.fromUriString(url);

		if (requestParams == null) {
			requestParams = new HashMap<>();
		}
		/** Adding "version" as requestparam for all the API calls to support upgrade */
		requestParams.put(RegistrationConstants.VERSION, environment.getProperty("mosip.id.regclient.current.version"));
		
		Set<String> set = requestParams.keySet();
		for (String queryParamName : set) {
			uriComponentsBuilder.queryParam(queryParamName, requestParams.get(queryParamName));

		}
		
		URI uri = uriComponentsBuilder.build().toUri();

		requestHTTPDTO.setUri(uri);

		LOGGER.debug("Completed preparing URI for web-service");
	}

	/**
	 * Setup of headers
	 * 
	 * @param httpHeaders
	 *            http headers
	 * @param headers
	 *            headers
	 */
	private void setHeaders(HttpHeaders httpHeaders, String headers) {
		LOGGER.debug("Preparing Header for web-service request");
		if(headers == null || headers.trim().isEmpty())
			return;

		String[] header = headers.split(",");
		String[] headerValues = null;
		//if (header != null) {
			for (String subheader : header) {
				if(subheader.trim().isEmpty())
					continue;

				//if (subheader != null) {
					headerValues = subheader.split(":");
					if (headerValues[0].equalsIgnoreCase("timestamp")) {
						headerValues[1] = DateUtils.formatToISOString(LocalDateTime.now());
					} else if (headerValues[0].equalsIgnoreCase("Center-Machine-RefId")) {
						headerValues[1] = String
								.valueOf(cernterId)
								.concat(RegistrationConstants.UNDER_SCORE).concat(String
										.valueOf(machineId));
					} else if (headerValues[0].equalsIgnoreCase("authorization")) {
						headerValues[1] = "auth";
					} else if (headerValues[0].equalsIgnoreCase("signature")) {
						headerValues[1] = "sign";
					}
					httpHeaders.add(headerValues[0], headerValues[1]);
				//}
			}
			httpHeaders.add("Cache-Control", "no-cache,max-age=0");
		//}

		LOGGER.debug("Completed preparing Header for web-service request");
	}

	/**
	 * @param requestHTTPDTO
	 *            create requestedHTTPDTO
	 * @param serviceName
	 *            service name to be called
	 * @param requestBody
	 *            object to be included in HTTP entities
	 */
	private void prepareRequest(RequestHTTPDTO requestHTTPDTO, String serviceName, Object requestBody) {
		LOGGER.debug("Preparing RequestHTTPDTO object for web-service");

		requestHTTPDTO.setHttpMethod(
				HttpMethod.valueOf(getEnvironmentProperty(serviceName, RegistrationConstants.HTTPMETHOD)));
		requestHTTPDTO.setHttpHeaders(new HttpHeaders());
		requestHTTPDTO.setRequestBody(requestBody);
		// Headers
		setHeaders(requestHTTPDTO.getHttpHeaders(), getEnvironmentProperty(serviceName, RegistrationConstants.HEADERS));

		LOGGER.debug("Completed preparing RequestHTTPDTO object for web-service");
	}


	private String getEnvironmentProperty(String serviceName, String serviceComponent) {
		return environment.getProperty(serviceName.concat(RegistrationConstants.DOT).concat(serviceComponent));
	}



	private boolean isResponseValid(Map<String, Object> responseMap, String key) {
		return !(null == responseMap || responseMap.isEmpty() || !responseMap.containsKey(key));
	}

}
