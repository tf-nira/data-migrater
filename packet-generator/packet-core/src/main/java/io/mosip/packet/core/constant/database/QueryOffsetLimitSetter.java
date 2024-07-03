package io.mosip.packet.core.constant.database;

public enum QueryOffsetLimitSetter {
    MSSQL("OFFSET <OFFSET> ROWS FETCH NEXT <LIMIT> ROWS ONLY"),
    ORACLE("OFFSET <OFFSET> ROWS FETCH NEXT <LIMIT> ROWS ONLY"),
    MYSQL("LIMIT <LIMIT>, <OFFSET>"),
    POSTGRESQL("OFFSET <OFFSET> LIMIT <LIMIT>");

    private final String type;

    QueryOffsetLimitSetter(String type) {
        this.type = type;
    }

    public String getValue(Long offSet, Long limit) {
        return type.replace("<OFFSET>", offSet.toString()).replace("<LIMIT>", limit.toString());
    }
}
