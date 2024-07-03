package io.mosip.packet.core.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

@Getter
@Setter
public class DataPostProcessorResponseDto implements Serializable {
    private static final long serialVersionUID = 1860051573487258579L;
    private String refId;
    private String trackerRefId;
    private Map<String, Object> responses;
    private String process;
}
