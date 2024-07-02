package io.mosip.packet.core.spi.dataexporter;

import io.mosip.packet.core.dto.DataPostProcessorResponseDto;
import io.mosip.packet.core.service.thread.ResultSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

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
    public Object export(DataPostProcessorResponseDto dataPostProcessorResponseDto, Long processStartTime, ResultSetter setter) throws Exception {
        return getExporter().export(dataPostProcessorResponseDto, processStartTime, setter);
    }
}