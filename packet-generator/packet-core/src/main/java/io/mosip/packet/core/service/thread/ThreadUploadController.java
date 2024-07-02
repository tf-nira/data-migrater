package io.mosip.packet.core.service.thread;

import io.mosip.packet.core.dto.DataPostProcessorResponseDto;
import lombok.Setter;

@Setter
public class ThreadUploadController extends BaseThreadController {
    private DataPostProcessorResponseDto result;
    private ThreadUploadProcessor processor;

    public ThreadUploadController() {
        failedCountIncrement = false;
    }

    @Override
    public void execute() throws Exception {
        processor.processData(setter, result);
    }
}
