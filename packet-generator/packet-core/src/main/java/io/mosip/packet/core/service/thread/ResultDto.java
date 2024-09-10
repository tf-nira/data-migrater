package io.mosip.packet.core.service.thread;

import io.mosip.packet.core.constant.tracker.TrackerStatus;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Data
@Getter
@Setter
public class ResultDto {
    private String refId;
    private String regNo;
    private String comments;
    private Map<String, Object> additionalMaps;
    private TrackerStatus status;
}
