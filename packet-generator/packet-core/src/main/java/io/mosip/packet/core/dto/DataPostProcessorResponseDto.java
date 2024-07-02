package io.mosip.packet.core.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

@Getter
@Setter
public class DataPostProcessorResponseDto implements Serializable {
    private String refId;
    private Map<String, Object> responses;
    private String process;
}
