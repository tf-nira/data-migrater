package io.mosip.packet.core.dto;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Data
@Setter
@Getter
public class TrackerAdditionalColumns implements Serializable {
    private String columnName;
    private String columnType;
    private Integer length;
    private Boolean isNotNull;
    private String idSchemaField;
}
