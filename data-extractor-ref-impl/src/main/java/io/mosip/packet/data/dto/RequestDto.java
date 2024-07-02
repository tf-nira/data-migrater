/**
 *
 */
package io.mosip.packet.data.dto;

import lombok.Data;

import java.util.List;

/**
 * Instantiates a new request dto.
 */
@Data
public class RequestDto {

    /** The identity. */
    private Object identity;

    /** The documents. */
    private List<Documents> documents;

    /** The registration id. */
    private String registrationId;

    private String status;


    private String biometricReferenceId;
    /** The UIN */
    private String uin;
    /** The entity. */
    private Object anonymousProfile;

    @Override
    public String toString() {
        return "RequestDto [identity=" + identity.toString() + ", registrationId=" + registrationId
                + ", status=" + status + ", biometricReferenceId" + biometricReferenceId + "uin" + uin + "]";
    }
}
