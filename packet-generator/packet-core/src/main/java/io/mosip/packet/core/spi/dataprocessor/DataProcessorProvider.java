package io.mosip.packet.core.spi.dataprocessor;

import io.mosip.packet.core.dto.DataProcessorResponseDto;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.service.thread.ResultSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataProcessorProvider implements DataProcessorApiFactory {
    @Autowired
    List<DataProcessor> dataProcessorList;

    private DataProcessor selectedProcessor;

    @Autowired
    Environment env;

    private DataProcessor getProcessor() throws Exception {
        String reProcessorClassName = env.getProperty("mosip.migrator.data.processor.auto.selection.classname");
        if(selectedProcessor != null && selectedProcessor.getClass().getName().equals(reProcessorClassName))
            return selectedProcessor;
        else {
            selectedProcessor = null;
            for(DataProcessor reader : dataProcessorList)
                if(reader.getClass().getName().equals(reProcessorClassName))
                    selectedProcessor= reader;

            if(selectedProcessor == null)
                throw new Exception("Implementation not Found for Data Reader Process " + reProcessorClassName);

            return selectedProcessor;
        }
    }

    @Override
    public DataProcessorResponseDto process(DBImportRequest dbImportRequest, Object data, ResultSetter setter) throws Exception {
        return getProcessor().process(dbImportRequest, data, setter);
    }
}
