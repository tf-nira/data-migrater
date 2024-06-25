package io.mosip.packet.core.constant.tracker;

public enum CharacterType {
    MSSQL("CHAR"),
    ORACLE("CHAR"),
    MYSQL("CHAR"),
    POSTGRESQL("CHARACTER");

    private final String type;

    CharacterType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
