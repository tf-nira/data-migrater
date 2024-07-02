package io.mosip.packet.core.spi.dataexporter;

import io.mosip.packet.core.dto.DataPostProcessorResponseDto;
import io.mosip.packet.core.service.thread.ResultSetter;

public interface DataExporter {
    public Object export(DataPostProcessorResponseDto dataPostProcessorResponseDto, Long processStartTime, ResultSetter setter) throws Exception;
}