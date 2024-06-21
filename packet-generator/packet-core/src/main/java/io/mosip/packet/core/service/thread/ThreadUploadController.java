package io.mosip.packet.core.service.thread;

import io.mosip.packet.core.dto.upload.PacketUploadDTO;
import lombok.Setter;

@Setter
public class ThreadUploadController extends BaseThreadController {
    private PacketUploadDTO result;
    private ThreadUploadProcessor processor;

    public ThreadUploadController() {
        failedCountIncrement = false;
    }

    @Override
    public void execute() throws Exception {
        processor.processData(setter, result);
    }
}
