package io.mosip.packet.core.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class DataProcessorResponseDto {
    private String refId;
    private Map<String, Object> responses;
    private String process;
}
