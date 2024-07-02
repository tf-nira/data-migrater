package io.mosip.packet.data.dto;

import lombok.Data;

@Data
public class BaseIdRequestResponseDTO {

    /**
     * The id.
     */
    private String id;

    /**
     * The ver.
     */
    private String version;

    /**
     * The timestamp.
     */
    private String responsetime;
}