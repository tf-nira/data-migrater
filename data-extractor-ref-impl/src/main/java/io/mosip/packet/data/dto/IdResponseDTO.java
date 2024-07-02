package io.mosip.packet.data.dto;

import com.fasterxml.jackson.annotation.JsonFilter;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString
@JsonFilter("responseFilter")
public class IdResponseDTO extends BaseIdRequestResponseDTO {

    /**
     * The err.
     */
    private List<ErrorDTO> errors;

    private Object metadata;

    /**
     * The response.
     */
    @JsonFilter("responseFilter")
    private ResponseDTO response;
}
