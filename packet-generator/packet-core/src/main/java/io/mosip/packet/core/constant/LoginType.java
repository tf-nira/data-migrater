package io.mosip.packet.core.constant;

public enum LoginType {
    PMS("pms"),
    REGPROC("regproc"),
    REGISTRATION("reg"),
    USER("user"),
    IDREPO("idrepo");

    private String loginCode;

    LoginType(String loginCode) {
        this.loginCode=loginCode;
    }

    public String getLoginCode() {
        return loginCode;
    }
}
