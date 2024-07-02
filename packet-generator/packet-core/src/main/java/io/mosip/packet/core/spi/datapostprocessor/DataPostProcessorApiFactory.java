package io.mosip.packet.core.spi.datapostprocessor;

import io.mosip.packet.core.dto.DataPostProcessorResponseDto;
import io.mosip.packet.core.dto.DataProcessorResponseDto;
import io.mosip.packet.core.service.thread.ResultSetter;

public interface DataPostProcessorApiFactory {
    public DataPostProcessorResponseDto postProcess(DataProcessorResponseDto processObject, ResultSetter setter, long startTime) throws Exception;
}
