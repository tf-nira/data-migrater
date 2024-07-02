package io.mosip.packet.data.dto;

import lombok.Data;

import java.util.List;

@Data
public class ResponseDTO {

    /**
     * The entity.
     */
    private Object anonymousProfile;

    private String biometricReferenceId;

    /**
     * The identity.
     */
    private Object identity;

    private List<Documents> documents;

    private String registrationId;

    /**
     * The status.
     */
    private String status;

    private String uin;
}
