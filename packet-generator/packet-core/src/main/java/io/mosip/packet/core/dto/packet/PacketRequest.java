package io.mosip.packet.core.dto.packet;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Data
public class PacketRequest {
    private boolean bypassCache;
    private String id;
    private List<String> modalities;
    private String person;
    private String process;
    private String source;
}
