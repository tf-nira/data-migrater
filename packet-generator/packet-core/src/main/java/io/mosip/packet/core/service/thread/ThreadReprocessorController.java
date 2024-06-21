package io.mosip.packet.core.service.thread;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.packet.core.entity.PacketTracker;
import lombok.Setter;

import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Setter
public class ThreadReprocessorController extends BaseThreadController {
    private ThreadReprocessor processor;
    private PacketTracker packetTracker;

    @Override
    public void execute() throws Exception {
        processor.processData(packetTracker);
    }
}
