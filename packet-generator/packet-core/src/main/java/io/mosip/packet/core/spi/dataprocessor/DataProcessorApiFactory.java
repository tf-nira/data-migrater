package io.mosip.packet.core.spi.dataprocessor;

import io.mosip.packet.core.dto.DataProcessorResponseDto;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.service.thread.ResultSetter;

public interface DataProcessorApiFactory {
    public DataProcessorResponseDto process(DBImportRequest dbImportRequest, Object data, ResultSetter setter) throws Exception;
}
