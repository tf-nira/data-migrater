package io.mosip.packet.manager.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.signature.constant.SignatureConstant;
import io.mosip.kernel.signature.dto.JWTSignatureVerifyRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureVerifyResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import io.mosip.packet.core.constant.MDMError;
import io.mosip.packet.core.constant.RegistrationConstants;

import io.mosip.packet.core.constant.RegistrationExceptionConstants;
import io.mosip.packet.core.logger.DataProcessLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * All helper methods commons to spec implementations
 *
 * @author anusha
 */
@Component
public class MosipDeviceSpecificationHelper {

	private static final Logger LOGGER = DataProcessLogger.getLogger(MosipDeviceSpecificationHelper.class);
	private static final String MDM_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'";

	private ObjectMapper mapper = new ObjectMapper();

	@Autowired
	private SignatureService signatureService;

	@Value("${mosip.registration.mdm.trust.domain.rcapture:DEVICE}")
	private String rCaptureTrustDomain;

	@Value("${mosip.registration.mdm.trust.domain.digitalId:DEVICE}")
	private String digitalIdTrustDomain;

	@Value("${mosip.registration.mdm.trust.domain.deviceinfo:DEVICE}")
	private String deviceInfoTrustDomain;

	private final String CONTENT_LENGTH = "Content-Length:";

	public String getPayLoad(String data) throws Exception {
		if (data == null || data.isEmpty()) {
			throw new Exception(RegistrationExceptionConstants.MDS_JWT_INVALID.getErrorCode() + " : " +
					RegistrationExceptionConstants.MDS_JWT_INVALID.getErrorMessage());
		}
		Pattern pattern = Pattern.compile(RegistrationConstants.BIOMETRIC_SEPERATOR);
		Matcher matcher = pattern.matcher(data);
		if (matcher.find()) {
			return matcher.group(1);
		}
		throw new Exception(RegistrationExceptionConstants.MDS_PAYLOAD_EMPTY.getErrorCode()+ " : " +
				RegistrationExceptionConstants.MDS_PAYLOAD_EMPTY.getErrorMessage());
	}
	
	public String getSignature(String data) throws Exception {
		if (data == null || data.isEmpty()) {
			throw new Exception(RegistrationExceptionConstants.MDS_JWT_INVALID.getErrorCode()+ " : " +
					RegistrationExceptionConstants.MDS_JWT_INVALID.getErrorMessage());
		}
		Pattern pattern = Pattern.compile(RegistrationConstants.BIOMETRIC_SEPERATOR);
		Matcher matcher = pattern.matcher(data);
		if(matcher.find()) {
			//returns header..signature
			return data.replace(matcher.group(1),"");
		}

		throw new Exception(RegistrationExceptionConstants.MDS_SIGNATURE_EMPTY.getErrorCode()+ " : " +
				RegistrationExceptionConstants.MDS_SIGNATURE_EMPTY.getErrorMessage());
	}

	public String getDigitalId(String data) throws Exception {
		if (data == null || data.isEmpty()) {
			throw new Exception(RegistrationExceptionConstants.MDS_JWT_INVALID.getErrorCode()+ " : " +
					RegistrationExceptionConstants.MDS_JWT_INVALID.getErrorMessage());
		}
		Pattern pattern = Pattern.compile(RegistrationConstants.BIOMETRIC_SEPERATOR);
		Matcher matcher = pattern.matcher(data);
		if(matcher.find()) {
			//returns header..signature
			return matcher.group(1);
		}

		throw new Exception(RegistrationExceptionConstants.MDS_SIGNATURE_EMPTY.getErrorCode()+ " : " +
				RegistrationExceptionConstants.MDS_SIGNATURE_EMPTY.getErrorMessage());
	}

	public void validateJWTResponse(final String signedData, final String domain) throws Exception {
		JWTSignatureVerifyRequestDto jwtSignatureVerifyRequestDto = new JWTSignatureVerifyRequestDto();
		jwtSignatureVerifyRequestDto.setValidateTrust(true);
		jwtSignatureVerifyRequestDto.setDomain(domain);
		jwtSignatureVerifyRequestDto.setJwtSignatureData(signedData);
		
		JWTSignatureVerifyResponseDto jwtSignatureVerifyResponseDto = signatureService.jwtVerify(jwtSignatureVerifyRequestDto);
		if(!jwtSignatureVerifyResponseDto.isSignatureValid())
				throw new Exception(MDMError.MDM_INVALID_SIGNATURE.getErrorCode()+ " : " + MDMError.MDM_INVALID_SIGNATURE.getErrorMessage());
		
		if (jwtSignatureVerifyRequestDto.getValidateTrust() && !jwtSignatureVerifyResponseDto.getTrustValid().equals(SignatureConstant.TRUST_VALID)) {
		      throw new Exception(MDMError.MDM_CERT_PATH_TRUST_FAILED.getErrorCode()+ " : " + MDMError.MDM_CERT_PATH_TRUST_FAILED.getErrorMessage());
		}
	}

	public String generateMDMTransactionId() {
		return UUID.randomUUID().toString();
	}

	public String buildUrl(int port, String endPoint) {
		return String.format("%s:%s/%s", getRunningurl(), port, endPoint);
	}

	private String getRunningurl() {
		return "http://127.0.0.1";
	}

	public ObjectMapper getMapper() {
		return mapper;
	}

	/**
	 * Using the urlStream get the next JPEG image as a byte[]
	 *
	 * @return byte[] of the JPEG
	 * @throws IOException
	 */
	public byte[] getJPEGByteArray(InputStream urlStream, long maxTimeLimit)
			throws Exception {

		int currByte = -1;

		boolean captureContentLength = false;
		StringWriter contentLengthStringWriter = new StringWriter(128);
		StringWriter headerWriter = new StringWriter(128);

		int contentLength = 0;

		while ((currByte = urlStream.read()) > -1) {
			if (captureContentLength) {
				if (currByte == 10 || currByte == 13) {
					contentLength = Integer.parseInt(contentLengthStringWriter.toString().replace(" ", ""));
					break;
				}
				contentLengthStringWriter.write(currByte);

			} else {
				headerWriter.write(currByte);
				String tempString = headerWriter.toString();
				int indexOf = tempString.indexOf(CONTENT_LENGTH);
				if (indexOf > 0) {
					captureContentLength = true;
				}
			}
			timeOutCheck(maxTimeLimit);
		}

		// 255 indicates the start of the jpeg image
		while (urlStream.read() != 255) {

			timeOutCheck(maxTimeLimit);
		}

		// rest is the buffer
		byte[] imageBytes = new byte[contentLength + 1];
		// since we ate the original 255 , shove it back in
		imageBytes[0] = (byte) 255;
		int offset = 1;
		int numRead = 0;
		while (offset < imageBytes.length
				&& (numRead = urlStream.read(imageBytes, offset, imageBytes.length - offset)) >= 0) {
			timeOutCheck(maxTimeLimit);
			offset += numRead;
		}

		return imageBytes;
	}

	private void timeOutCheck(long maxTimeLimit) throws Exception {

		if (System.currentTimeMillis() > maxTimeLimit) {

			throw new Exception(RegistrationExceptionConstants.MDS_STREAM_TIMEOUT.getErrorCode()+ " : " +
					RegistrationExceptionConstants.MDS_STREAM_TIMEOUT.getErrorMessage());
		}
	}
	

	public void validateQualityScore(String qualityScore) throws Exception {
		if (qualityScore == null || qualityScore.isEmpty()) {
			throw new Exception(
					RegistrationExceptionConstants.MDS_RCAPTURE_ERROR.getErrorCode()+ " : " +
					RegistrationExceptionConstants.MDS_RCAPTURE_ERROR.getErrorMessage()
							+ " Identified Quality Score for capture biometrics is null or Empty");
		}
	}
}
