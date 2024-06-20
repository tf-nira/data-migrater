package io.mosip.packet.core.spi;

import io.mosip.commons.packet.dto.packet.PacketDto;

public interface DataExporter {
    public String export(PacketDto packetDto) throws Exception;
}
