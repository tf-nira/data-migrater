package io.mosip.packet.core.dto.dbimport;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Data
public class FieldName {
    private String tableName;
    private String modifiedFieldName;
    private String originalFieldName;
}
