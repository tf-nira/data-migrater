package io.mosip.packet.data.dataexporter;

import com.google.gson.Gson;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.GlobalConfig;
import io.mosip.packet.core.constant.activity.ActivityName;
import io.mosip.packet.core.constant.tracker.TrackerStatus;
import io.mosip.packet.core.dto.DataPostProcessorResponseDto;
import io.mosip.packet.core.dto.upload.PacketUploadDTO;
import io.mosip.packet.core.dto.upload.PacketUploadResponseDTO;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.service.thread.ResultDto;
import io.mosip.packet.core.service.thread.ResultSetter;
import io.mosip.packet.core.spi.dataexporter.DataExporter;
import io.mosip.packet.core.util.TrackerUtil;
import io.mosip.packet.core.util.regclient.ConfigUtil;
import io.mosip.packet.uploader.service.PacketUploaderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static io.mosip.packet.core.constant.GlobalConfig.SESSION_KEY;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
public class MosipPacketExporter implements DataExporter {
    private static final Logger LOGGER = DataProcessLogger.getLogger(MosipPacketExporter.class);

    @Autowired
    private TrackerUtil trackerUtil;

    @Autowired
    PacketUploaderService packetUploaderService;


    @Override
    public Object export(DataPostProcessorResponseDto dataPostProcessorResponseDto, Long processStartTime, ResultSetter setter) throws Exception {
        String refId = dataPostProcessorResponseDto.getTrackerRefId();
        PacketUploadDTO uploadDTO = (PacketUploadDTO) dataPostProcessorResponseDto.getResponses().get("uploadDTO");

        List<PacketUploadDTO> uploadList = new ArrayList<>();
        uploadList.add(uploadDTO);
        HashMap<String, PacketUploadResponseDTO> response = new HashMap<>();
        packetUploaderService.syncPacket(uploadList, ConfigUtil.getConfigUtil().getCenterId(), ConfigUtil.getConfigUtil().getMachineId(), response);
        trackerUtil.addTrackerLocalEntry(dataPostProcessorResponseDto.getRefId(), uploadDTO.getPacketId(), TrackerStatus.SYNCED, null, uploadList, SESSION_KEY, GlobalConfig.getActivityName());
        packetUploaderService.uploadSyncedPacket(uploadList, response);
        LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Packet Upload Response for " + refId + " : " + (new Gson()).toJson(response));
        ResultDto resultDto = new ResultDto();
        resultDto.setRegNo(uploadDTO.getPacketId());
        resultDto.setRefId(dataPostProcessorResponseDto.getRefId());
        resultDto.setComments((new Gson()).toJson(response));
        resultDto.setStatus(GlobalConfig.getApplicableActivityList().contains(ActivityName.DATA_EXPORTER) ? TrackerStatus.PROCESSED : TrackerStatus.PROCESSED_WITHOUT_UPLOAD);
        setter.setResult(resultDto);
        return "Packet Upload Success for " + refId;
    }

}
