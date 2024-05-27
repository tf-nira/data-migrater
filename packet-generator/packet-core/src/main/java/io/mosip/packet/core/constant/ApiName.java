package io.mosip.packet.core.constant;

/**
 * 
 * @author Sowmya
 *
 */
public enum ApiName {
	DOCUMENT_CATEGORY(LoginType.REGISTRATION),
	DOCUMENT_TYPES(LoginType.REGISTRATION),
	LATEST_ID_SCHEMA(LoginType.REGISTRATION),
	BIOSDK_QUALITY_CHECK(LoginType.REGPROC),
	MASTER_VALIDATOR_SERVICE_NAME(LoginType.REGISTRATION),
	GET_CERTIFICATE(LoginType.REGISTRATION),
	PACKET_SYNC_V2(LoginType.USER),
	PACKET_SYNC(LoginType.USER),
	PACKET_UPLOAD(LoginType.USER),
	KERNEL_DECRYPT(LoginType.REGPROC),
	BQAT_BIOSDK_QUALITY_CHECK(LoginType.REGPROC),
	MASTER_MACHINE_SEARCH(LoginType.USER),
	MASTER_MACHINE_CREATE(LoginType.USER),
    MASTER_MACHINE_ACTIVATE(LoginType.USER),
	PACKET_BIOMETRIC_READER(LoginType.REGPROC);

	private LoginType loginType;

	ApiName(LoginType loginType) {
		this.loginType = loginType;
	}

	public LoginType getLoginType() {
		return loginType;
	}
}
