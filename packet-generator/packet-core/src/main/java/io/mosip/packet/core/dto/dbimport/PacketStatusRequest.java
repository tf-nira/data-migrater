package io.mosip.packet.core.dto.dbimport;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class PacketStatusRequest {
    private String nin;
}
