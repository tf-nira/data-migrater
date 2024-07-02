package io.mosip.packet.core.service.thread;

import io.mosip.packet.core.dto.DataPostProcessorResponseDto;

public interface ThreadUploadProcessor {
    public void processData(ResultSetter setter, DataPostProcessorResponseDto result) throws Exception;
}
