package io.mosip.packet.core.spi.dataexporter;

import io.mosip.commons.packet.dto.packet.PacketDto;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.service.thread.ResultSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;

@Component
public class DataExporterProvider implements DataExporterApiFactory {
    @Autowired
    List<DataExporter> dataExporterList;

    private DataExporter selectedExporter;

    @Autowired
    Environment env;

    private DataExporter getExporter() throws Exception {
        String exporterClassName = env.getProperty("mosip.migrator.data.exporter.auto.selection.classname");
        if(selectedExporter != null && selectedExporter.getClass().getName().equals(exporterClassName))
            return selectedExporter;
        else {
            selectedExporter = null;
            for(DataExporter reader : dataExporterList)
                if(reader.getClass().getName().equals(exporterClassName))
                    selectedExporter= reader;

            if(selectedExporter == null)
                throw new Exception("Implementation not Found for Data Exporter Process " + exporterClassName);
            return selectedExporter;
        }
    }

    @Override
    public Object export(PacketDto packetDto, DBImportRequest dbImportRequest, HashMap<String, String> metaInfo, HashMap<String, Object> demoDetails,
                         String trackerColumn, ResultSetter setter, String trackerRefid, Long startTime) throws Exception {
        return getExporter().export(packetDto, dbImportRequest, metaInfo, demoDetails, trackerColumn, setter, trackerRefid, startTime);
    }
}