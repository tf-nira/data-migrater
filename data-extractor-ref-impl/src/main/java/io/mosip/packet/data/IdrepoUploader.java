package io.mosip.packet.data;

import com.google.gson.Gson;
import io.mosip.commons.packet.dto.packet.PacketDto;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.service.thread.ResultSetter;
import io.mosip.packet.core.spi.dataexporter.DataExporter;
import org.springframework.stereotype.Component;

import java.util.HashMap;

@Component
public class IdrepoUploader implements DataExporter {
    @Override
    public Object export(PacketDto packetDto, DBImportRequest dbImportRequest, HashMap<String, String> metaInfo, HashMap<String, Object> demoDetails,
                         String trackerColumn, ResultSetter setter) throws Exception {
        System.out.println("Entering Idrepo Data " + (new Gson()).toJson(packetDto));
        return null;
    }
}
