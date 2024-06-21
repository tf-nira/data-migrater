package io.mosip.packet.core.spi.datareprocessor;

import io.mosip.packet.core.constant.FieldCategory;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.service.thread.ResultSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DataReProcessorProvider implements DataReProcessorApiFactory {
    @Autowired
    List<DataReProcessor> dataReProcessorList;

    private DataReProcessor selectedReProcessor;

    @Autowired
    Environment env;

    private DataReProcessor getReProcessor() throws Exception {
        String reProcessorClassName = env.getProperty("mosip.migrator.data.reprocessor.auto.selection.classname");
        if(selectedReProcessor != null && selectedReProcessor.getClass().getName().equals(reProcessorClassName))
            return selectedReProcessor;
        else {
            selectedReProcessor = null;
            for(DataReProcessor reader : dataReProcessorList)
                if(reader.getClass().getName().equals(reProcessorClassName))
                    selectedReProcessor= reader;

            if(selectedReProcessor == null)
                throw new Exception("Implementation not Found for Data Reader Process " + reProcessorClassName);

            return selectedReProcessor;
        }
    }

    @Override
    public void reProcess() throws Exception {
        getReProcessor().reProcess();
    }

    @Override
    public void connectDataReader(DBImportRequest dbImportRequest) throws Exception {
        getReProcessor().connectDataReader();
    }

    @Override
    public void disconnectDataReader() throws Exception {
        getReProcessor().disconnectDataReader();
    }
}
