package io.mosip.packet.core.dto.dbimport;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
@Getter
@Setter
public class DocumentValueMap {
    private String mapColumnName;
    private String mapColumnValue;
    private List<String> fieldList;

    public String getColumnNameWithoutSchema(){
        if(mapColumnName.contains(".")) {
            return mapColumnName.split("\\.")[1].toUpperCase();
        } else {
            return mapColumnName.toUpperCase();
        }
    }
}
