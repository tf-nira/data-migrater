package io.mosip.packet.core.util;

import com.google.gson.Gson;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.config.login.LoginConfig;
import io.mosip.packet.core.constant.LoggerFileConstant;
import io.mosip.packet.core.constant.LoginType;
import io.mosip.packet.core.dto.PasswordRequest;
import io.mosip.packet.core.dto.request.ClientSecretKeyRequest;
import io.mosip.packet.core.dto.request.Metadata;
import io.mosip.packet.core.dto.request.SecretKeyRequest;
import io.mosip.packet.core.dto.request.TokenRequestDTO;
import io.mosip.packet.core.exception.TokenGenerationFailedException;
import io.mosip.packet.core.logger.DataProcessLogger;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.mosip.packet.core.constant.GlobalConfig.IS_NETWORK_AVAILABLE;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

/**
 * The Class RestApiClient.
 *
 * @author Rishabh Keshari
 */
@Component
public class RestApiClient {

	@Value("${registration.processor.httpclient.connections.max.per.host:20}")
	private int maxConnectionPerRoute;

	@Value("${registration.processor.httpclient.connections.max:100}")
	private int totalMaxConnection;

	/** The logger. */
	private Logger logger = DataProcessLogger.getLogger(RestApiClient.class);

	/** The builder. */
	@Autowired
	RestTemplateBuilder builder;

	@Autowired
	Environment environment;

	private static final String AUTHORIZATION = "Authorization=";

	RestTemplate localRestTemplate;

	private static Boolean isAuthRequired = true;

	public static void setIsAuthRequired(Boolean isAuthRequired) {
		RestApiClient.isAuthRequired = isAuthRequired;
	}

	private LoginType loginType;

	private LoginType currentLoginType;

	@Autowired
	private LoginConfig loginConfig;

	@PostConstruct
	private void loadRestTemplate() throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
		localRestTemplate = getRestTemplate();
		logger.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.APPLICATIONID.toString(),
				LoggerFileConstant.APPLICATIONID.toString(), "loadRestTemplate completed successfully");
	}
	/**
	 * Gets the api. *
	 *
	 * @param              <T> the generic type
	 * @param  <T>     the get URI
	 * @param responseType the response type
	 * @return the api
	 * @throws Exception
	 */
	@SuppressWarnings("unchecked")
	public <T> T getApi(URI uri, Class<?> responseType, LoginType loginType, String trackerRefId) throws Exception {
		this.loginType = loginType;
		T result = null;
		try {
			Long startTime = System.nanoTime();
			result = (T) localRestTemplate.exchange(uri, HttpMethod.GET, setRequestHeader(null, null), responseType)
					.getBody();
			logger.debug("SESSION_ID", APPLICATION_NAME, "getApi()", "Time Taken for GET Api Call " + uri.toString() + " Reference Id: " + trackerRefId + " (" + TimeUnit.MILLISECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS) + " ms)");
		} catch (Exception e) {
			logger.error(LoggerFileConstant.SESSIONID.toString(), APPLICATION_NAME,
					APPLICATION_ID, e.getMessage() + ExceptionUtils.getStackTrace(e));
			throw e;
		}
		return result;
	}

	/**
	 * Post api.
	 *
	 * @param <T>
	 *            the generic type
	 * @param uri
	 *            the uri
	 * @param requestType
	 *            the request type
	 * @param responseClass
	 *            the response class
	 * @return the t
	 */
	@SuppressWarnings("unchecked")
	public <T> T postApi(String uri, MediaType mediaType, Object requestType, Class<?> responseClass, LoginType loginType, String trackerRefId) throws Exception {
		this.loginType = loginType;
		T result = null;
		try {
			logger.info(LoggerFileConstant.SESSIONID.toString(), APPLICATION_NAME,
					APPLICATION_ID, uri);
			Long startTime = System.nanoTime();
			result = (T) localRestTemplate.postForObject(uri, setRequestHeader(requestType, mediaType), responseClass);
			logger.debug("SESSION_ID", APPLICATION_NAME, "postApi()", "Time Taken for POST Api Call " + uri.toString() + " Reference Id: " + trackerRefId + " (" + TimeUnit.MILLISECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS) + " ms)");
		} catch (Exception e) {
			logger.error(LoggerFileConstant.SESSIONID.toString(), APPLICATION_NAME,
					APPLICATION_ID, e.getMessage() + ExceptionUtils.getStackTrace(e));

			throw e;
		}
		return result;
	}

	/**
	 * Patch api.
	 *
	 * @param <T>
	 *            the generic type
	 * @param uri
	 *            the uri
	 * @param requestType
	 *            the request type
	 * @param responseClass
	 *            the response class
	 * @return the t
	 */
	@SuppressWarnings("unchecked")
	public <T> T patchApi(String uri, MediaType mediaType, Object requestType, Class<?> responseClass, LoginType loginType, String trackerRefId)
			throws Exception {
		this.loginType = loginType;
		RestTemplate restTemplate;
		T result = null;
		try {
			logger.info(LoggerFileConstant.SESSIONID.toString(), APPLICATION_NAME,
					APPLICATION_ID, uri);
			Long startTime = System.nanoTime();
			result = (T) localRestTemplate.patchForObject(uri, setRequestHeader(requestType, mediaType), responseClass);
			logger.debug("SESSION_ID", APPLICATION_NAME, "patchApi()", "Time Taken for PATCH Api Call " + uri.toString() + " Reference Id: " + trackerRefId + " (" + TimeUnit.MILLISECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS) + " ms)");
		} catch (Exception e) {

			logger.error(LoggerFileConstant.SESSIONID.toString(), APPLICATION_NAME,
					APPLICATION_ID, e.getMessage() + ExceptionUtils.getStackTrace(e));

			throw e;
		}
		return result;
	}

	public <T> T patchApi(String uri, Object requestType, Class<?> responseClass, LoginType loginType, String trackerRefId) throws Exception {
		return patchApi(uri, null, requestType, responseClass, loginType, trackerRefId);
	}

	/**
	 * Put api.
	 *
	 * @param <T>
	 *            the generic type
	 * @param uri
	 *            the uri
	 * @param requestType
	 *            the request type
	 * @param responseClass
	 *            the response class
	 * @param mediaType
	 * @return the t
	 * @throws Exception
	 *             the exception
	 */
	@SuppressWarnings("unchecked")
	public <T> T putApi(String uri, Object requestType, Class<?> responseClass, MediaType mediaType, LoginType loginType, String trackerRefId) throws Exception {
		this.loginType = loginType;
		T result = null;
		ResponseEntity<T> response = null;
		try {
			logger.info(LoggerFileConstant.SESSIONID.toString(), APPLICATION_NAME,
					APPLICATION_ID, uri);
			Long startTime = System.nanoTime();
			response = (ResponseEntity<T>) localRestTemplate.exchange(uri, HttpMethod.PUT,
					setRequestHeader(requestType, mediaType), responseClass);
			logger.debug("SESSION_ID", APPLICATION_NAME, "putApi()", "Time Taken for PUT Api Call " + uri.toString() + " Reference Id: " + trackerRefId + " (" + TimeUnit.MILLISECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS) + " ms)");
			result = response.getBody();
		} catch (Exception e) {

			logger.error(LoggerFileConstant.SESSIONID.toString(), APPLICATION_NAME,
					APPLICATION_ID, e.getMessage() + ExceptionUtils.getStackTrace(e));

			throw e;
		}
		return result;
	}

	public RestTemplate getRestTemplate() throws KeyManagementException, NoSuchAlgorithmException, KeyStoreException {
		if(localRestTemplate != null)
			return localRestTemplate;

		logger.info(LoggerFileConstant.SESSIONID.toString(), APPLICATION_NAME,
				APPLICATION_ID, Arrays.asList(environment.getActiveProfiles()).toString());
		if (Arrays.stream(environment.getActiveProfiles()).anyMatch("dev-k8"::equals)) {
			logger.info(LoggerFileConstant.SESSIONID.toString(), LoggerFileConstant.APPLICATIONID.toString(),
					LoggerFileConstant.APPLICATIONID.toString(),
					Arrays.asList(environment.getActiveProfiles()).toString());
			return new RestTemplate();
		} else {
			System.out.println("Max Connection for RestAPI Call " + maxConnectionPerRoute);
			HttpClientBuilder httpClientBuilder = HttpClients.custom()
					.setMaxConnPerRoute(maxConnectionPerRoute)
					.setMaxConnTotal(totalMaxConnection).disableCookieManagement();
			HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
			requestFactory.setHttpClient(httpClientBuilder.build());
			return new RestTemplate(requestFactory);
		}

	}

	/**
	 * this method sets token to header of the request
	 *
	 * @param requestType
	 * @param mediaType
	 * @return
	 * @throws IOException
	 */
	@SuppressWarnings("unchecked")
	private HttpEntity<Object> setRequestHeader(Object requestType, MediaType mediaType) throws Exception {
		MultiValueMap<String, String> headers = new LinkedMultiValueMap<String, String>();
		if(IS_NETWORK_AVAILABLE && isAuthRequired)
			headers.add("Cookie", getToken());
		if (mediaType != null) {
			headers.add("Content-Type", mediaType.toString());
		}
		if (requestType != null) {
			try {
				HttpEntity<Object> httpEntity = (HttpEntity<Object>) requestType;
				HttpHeaders httpHeader = httpEntity.getHeaders();
				Iterator<String> iterator = httpHeader.keySet().iterator();
				while (iterator.hasNext()) {
					String key = iterator.next();
					if (!(headers.containsKey("Content-Type") && key == "Content-Type"))
						headers.add(key, httpHeader.get(key).get(0));
				}
				return new HttpEntity<Object>(httpEntity.getBody(), headers);
			} catch (ClassCastException e) {
				return new HttpEntity<Object>(requestType, headers);
			}
		} else
			return new HttpEntity<Object>(headers);
	}

	/**
	 * This method gets the token for the user details present in config server.
	 *
	 * @return
	 * @throws IOException
	 */
	public String getToken() throws Exception {
		String token = System.getProperty("token");
		boolean isValid = false;

		if(isAuthRequired) {
			if (StringUtils.isNotEmpty(token) && currentLoginType.equals(loginType)) {
				isValid = TokenHandlerUtil.isValidBearerToken(token, environment.getProperty("token.request.issuerUrl"),
						getIssuer(loginType));
			}

			if (!isValid) {
				TokenRequestDTO<Object> tokenRequestDTO = new TokenRequestDTO<Object>();
				tokenRequestDTO.setId(environment.getProperty("token.request.id"));
				tokenRequestDTO.setMetadata(new Metadata());

				tokenRequestDTO.setRequesttime(DateUtils.getUTCCurrentDateTimeString());
				// tokenRequestDTO.setRequest(setPasswordRequestDTO());
				tokenRequestDTO.setRequest(setSecretKeyRequestDTO(loginType));
				tokenRequestDTO.setVersion(environment.getProperty("token.request.version"));

				Gson gson = new Gson();
				HttpClient httpClient = HttpClientBuilder.create().build();
				// HttpPost post = new
				// HttpPost(environment.getProperty("PASSWORDBASEDTOKENAPI"));
				HttpPost post = null;
				if(loginType.equals(LoginType.USER))
					post = new HttpPost(environment.getProperty("USERBASEDTOKENAPI"));
				else
					post = new HttpPost(environment.getProperty("KEYBASEDTOKENAPI"));

				try {
					StringEntity postingString = new StringEntity(gson.toJson(tokenRequestDTO));
					post.setEntity(postingString);
					post.setHeader("Content-type", "application/json");
					Long startTime = System.nanoTime();
					HttpResponse response = httpClient.execute(post);
					logger.debug("SESSION_ID", APPLICATION_NAME, "getToken()", "Time Taken for AUTH Api Call " + post.getURI().toString() + " (" + TimeUnit.MILLISECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS) + " ms)");
					org.apache.http.HttpEntity entity = response.getEntity();
					String responseBody = EntityUtils.toString(entity, "UTF-8");
					if(!loginType.equals(LoginType.USER)) {
						Header[] cookie = response.getHeaders("Set-Cookie");
						if (cookie.length == 0)
							throw new TokenGenerationFailedException();
						token = response.getHeaders("Set-Cookie")[0].getValue();
						System.setProperty("token", token.substring(14, token.indexOf(';')));
						currentLoginType = loginType;
						return token.substring(0, token.indexOf(';'));
					} else {
						JSONParser jsonParser = new JSONParser();
						JSONObject responseJsonObject = (JSONObject) jsonParser.parse(responseBody);
						JSONObject tokenJsonObject = (JSONObject) responseJsonObject.get("response");
						token = tokenJsonObject.get("token").toString();
						System.setProperty("token", token);
						currentLoginType = loginType;
						return AUTHORIZATION + token;
					}
				} catch (IOException | ParseException e) {
					logger.error(LoggerFileConstant.SESSIONID.toString(), APPLICATION_NAME,
							APPLICATION_ID, e.getMessage() + ExceptionUtils.getStackTrace(e));
					throw e;
				}
			}
			return AUTHORIZATION + token;
		} else {
			return null;
		}
	}

	private String getIssuer(LoginType loginType) throws Exception {
		Map<String, String> credential = loginConfig.getCredentials().get(loginType.getLoginCode());
		if(credential.containsKey("clientId"))
			return credential.get("clientId");
		else if(credential.containsKey("username"))
			return credential.get("username");
		else {
			logger.error(LoggerFileConstant.SESSIONID.toString(), APPLICATION_NAME,
					APPLICATION_ID, "Client Id or User Name (clientId / username) not configured for the login type " + loginType.getLoginCode());
			throw new Exception("Client Id or User Name (clientId / username) not configured for the login type " + loginType.getLoginCode());
		}
	}

	private Object setSecretKeyRequestDTO(LoginType loginType) throws Exception {
		if(!loginType.equals(LoginType.USER)) {
			SecretKeyRequest request = new SecretKeyRequest();
			request.setAppId(getIssuerAppId(loginType));
			request.setClientId(getIssuer(loginType));
			request.setSecretKey(getIssuerPassword(loginType));
			return request;
		} else {
			ClientSecretKeyRequest request = new ClientSecretKeyRequest();
			request.setAppId(getIssuerAppId(LoginType.REGISTRATION));
			request.setClientId(getIssuer(LoginType.REGISTRATION));
			request.setClientSecret(getIssuerPassword(LoginType.REGISTRATION));
			request.setUserName(getIssuer(loginType));
			request.setPassword(getIssuerPassword(loginType));
			return request;
		}
	}

	private String getIssuerPassword(LoginType loginType) throws Exception {
		Map<String, String> credential = loginConfig.getCredentials().get(loginType.getLoginCode());
		if(credential.containsKey("secretKey"))
			return credential.get("secretKey");
		else if(credential.containsKey("password"))
			return credential.get("password");
		else {
			logger.error(LoggerFileConstant.SESSIONID.toString(), APPLICATION_NAME,
					APPLICATION_ID, "Secret Key or Password (secretKey / password) not configured for the login type " + loginType.getLoginCode());
			throw new Exception("Secret Key or Password (secretKey / password) not configured for the login type " + loginType.getLoginCode());
		}
	}

	private String getIssuerAppId(LoginType loginType) throws Exception {
		Map<String, String> credential = loginConfig.getCredentials().get(loginType.getLoginCode());
		if(credential.containsKey("appid"))
			return credential.get("appid");
		else {
			logger.error(LoggerFileConstant.SESSIONID.toString(), APPLICATION_NAME,
					APPLICATION_ID, "App Id (appid) not configured for the login type " + loginType.getLoginCode());
			throw new Exception("App Id (appid) not configured for the login type " + loginType.getLoginCode());
		}

	}

	private PasswordRequest setPasswordRequestDTO() {

		PasswordRequest request = new PasswordRequest();
		request.setAppId(environment.getProperty("token.request.appid"));
		request.setPassword(environment.getProperty("token.request.password"));
		request.setUserName(environment.getProperty("token.request.username"));
		return request;
	}

	public <T> T  invoke(URI uri, HttpMethod httpMethod, HttpEntity<?> entity, Class<?> responseClass, SimpleClientHttpRequestFactory factory, String trackerRefId) throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException, IOException {
		RestTemplate restTemplate;
		T result = null;
		try {
			restTemplate = getRestTemplate();
			if (factory != null)
				restTemplate.setRequestFactory(factory);

	//		entity.add("Cookie", getToken());
			Long startTime = System.nanoTime();
			result = (T) restTemplate.exchange(uri, httpMethod, entity, responseClass).getBody();
			logger.debug("SESSION_ID", APPLICATION_NAME, "invoke()", "Time Taken for "+ httpMethod.name() + " Api Call " + uri.toString() + " Reference Id: " + trackerRefId + " (" + TimeUnit.MILLISECONDS.convert(System.nanoTime()-startTime, TimeUnit.NANOSECONDS) + " ms)");

		} catch (Exception e) {
			logger.error(LoggerFileConstant.SESSIONID.toString(), APPLICATION_NAME,
					APPLICATION_ID, e.getMessage() + ExceptionUtils.getStackTrace(e));
			throw e;
		}
		return result;
	}
}
