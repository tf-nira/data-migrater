package io.mosip.packet.data.dto;

import lombok.Data;

@Data
public class IdRequestDto {

    /**
     * The id.
     */
    private String id;

    /**
     * The request.
     */
    private RequestDto request;

    /**
     * The time stamp.
     */
    private String requesttime;

    /**
     * The version.
     */
    private String version;

    private Object metadata;

}
