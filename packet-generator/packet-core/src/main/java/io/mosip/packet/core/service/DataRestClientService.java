package io.mosip.packet.core.service;

import io.mosip.packet.core.constant.ApiName;
import io.mosip.packet.core.exception.ApisResourceAccessException;
import io.mosip.packet.core.util.regclient.RequestHTTPDTO;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Map;

/**
 * The Interface RegistrationProcessorRestClientService.
 *
 * @author Rishabh Keshari
 * @param <T>
 *            the generic type
 */
public interface DataRestClientService<T> {

	/**
	 * Gets the api.
	 *
	 * @param apiName
	 *            the api name
	 * @param pathsegments
	 *            pathsegments of the uri
	 * @param queryParam
	 *            the query param
	 * @param queryParamValue
	 *            the query param value
	 * @param responseType
	 *            the response type
	 * @return the api
	 * @throws ApisResourceAccessException
	 *             the apis resource access exception
	 */
	public T getApi(ApiName apiName, List<String> pathsegments, String queryParam, String queryParamValue,
					Class<?> responseType, String trackerRefId) throws ApisResourceAccessException;

	public T getApi(ApiName apiName, List<String> pathsegments, List<String> queryParam, List<Object> queryParamValue,
					Class<?> responseType, String trackerRefId) throws ApisResourceAccessException;

	/**
	 * Post api.
	 *
	 * @param apiName
	 *            the api name
	 * @param queryParam
	 *            the query param
	 * @param queryParamValue
	 *            the query param value
	 * @param requestedData
	 *            the requested data
	 * @param responseType
	 *            the response type
	 * @return the t
	 * @throws ApisResourceAccessException
	 *             the apis resource access exception
	 */
	public T postApi(ApiName apiName, String queryParam, String queryParamValue, T requestedData, Class<?> responseType, String trackerRefId)
			throws ApisResourceAccessException;

	public T postApi(ApiName apiName, String queryParam, String queryParamValue, T requestedData, Class<?> responseType, boolean isAuthRequired, String trackerRefId)
			throws ApisResourceAccessException;

	public T postApi(ApiName apiName, String queryParam, String queryParamValue, T requestedData, Class<?> responseType, MediaType mediaType, boolean isAuthRequired, String trackerRefId)
			throws ApisResourceAccessException;

	public T postApi(String apiHostIpPort, String queryParam, String queryParamValue, T requestedData, Class<?> responseType, boolean isAuthRequired, ApiName apiName, String trackerRefId)
			throws ApisResourceAccessException;

	/**
	 * Post api.
	 *
	 * @param apiName
	 *            the api name
	 * @param queryParamName
	 *            the query param
	 * @param queryParamValue
	 *            the query param value
	 * @param requestedData
	 *            the requested data
	 * @param responseType
	 *            the response type
	 * @param mediaType
	 * 			  the content type    
	 *        
	 * @return the t
	 * @throws ApisResourceAccessException
	 *             the apis resource access exception
	 */
	public T postApi(ApiName apiName, String queryParamName, String queryParamValue, T requestedData,
			Class<?> responseType, MediaType mediaType, String trackerRefId) throws ApisResourceAccessException ;
	/**
	 * Post api.
	 *
	 * @param apiName
	 *            the api name
	 * @param pathsegments
	 *            the pathsegments
	 * @param queryParam
	 *            the query param
	 * @param queryParamValue
	 *            the query param value
	 * @param requestedData
	 *            the requested data
	 * @param responseType
	 *            the response type
	 * @return the t
	 * @throws ApisResourceAccessException
	 *             the apis resource access exception
	 */

	public T postApi(ApiName apiName, List<String> pathsegments, String queryParam, String queryParamValue,
			T requestedData, Class<?> responseType, String trackerRefId) throws ApisResourceAccessException;

	/**
	 * Post Api
	 *
	 * @param apiName
	 * @param mediaType
	 * @param pathsegments
	 * @param queryParam
	 * @param queryParamValue
	 * @param requestedData
	 * @param responseType
	 * @return
	 * @throws ApisResourceAccessException
	 */
	public T postApi(ApiName apiName, MediaType mediaType, List<String> pathsegments, List<String> queryParam, List<Object> queryParamValue,
					 T requestedData, Class<?> responseType, String trackerRefId) throws ApisResourceAccessException;

	public Map<String, Object> invokeURL(RequestHTTPDTO requestHTTPDTO, String trackerRefId) throws Exception;

	public T putApi(ApiName apiName, String queryParamName, String queryParamValue, T requestedData,
					 Class<?> responseType, MediaType mediaType, String trackerRefId) throws ApisResourceAccessException ;

	public T patchApi(ApiName apiName, List<String> pathsegments, String queryParam, String queryParamValue,
					  T requestedData, Class<?> responseType, String trackerRefId) throws ApisResourceAccessException;
}
