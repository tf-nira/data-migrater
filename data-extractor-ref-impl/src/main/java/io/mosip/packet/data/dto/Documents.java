package io.mosip.packet.data.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Documents {

    /**
     * The doc type.
     */
    private String category;

    /**
     * The doc value.
     */
    private String value;
}
