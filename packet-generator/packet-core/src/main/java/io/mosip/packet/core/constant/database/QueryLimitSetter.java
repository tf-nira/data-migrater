package io.mosip.packet.core.constant.database;

import com.amazonaws.services.dynamodbv2.xspec.L;

public enum QueryLimitSetter {
    MSSQL("FETCH NEXT %s ROWS ONLY"),
    ORACLE("FETCH NEXT %s ROWS ONLY"),
    MYSQL("LIMIT %s"),
    POSTGRESQL("LIMIT %s");

    private final String type;

    QueryLimitSetter(String type) {
        this.type = type;
    }

    public String getValue(Integer val) {
        return String.format(type, val);
    }
}
