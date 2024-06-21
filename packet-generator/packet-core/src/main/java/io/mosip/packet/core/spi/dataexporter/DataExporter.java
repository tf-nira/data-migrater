package io.mosip.packet.core.spi.dataexporter;

import io.mosip.commons.packet.dto.packet.PacketDto;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.service.thread.ResultSetter;

import java.util.HashMap;

public interface DataExporter {
    public Object export(PacketDto packetDto, DBImportRequest dbImportRequest, HashMap<String, String> metaInfo, HashMap<String, Object> demoDetails,
                         String trackerColumn, ResultSetter setter) throws Exception;
}