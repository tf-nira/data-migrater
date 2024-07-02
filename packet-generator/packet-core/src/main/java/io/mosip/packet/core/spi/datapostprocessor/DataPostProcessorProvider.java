package io.mosip.packet.core.spi.datapostprocessor;

import io.mosip.packet.core.dto.DataPostProcessorResponseDto;
import io.mosip.packet.core.dto.DataProcessorResponseDto;
import io.mosip.packet.core.service.thread.ResultSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DataPostProcessorProvider implements DataPostProcessorApiFactory {
    @Autowired
    List<DataPostProcessor> dataPostProcessorList;

    private DataPostProcessor selectedPostProcessor;

    @Autowired
    Environment env;

    private DataPostProcessor getPostProcessor() throws Exception {
        String reProcessorClassName = env.getProperty("mosip.migrator.data.post.processor.auto.selection.classname");
        if(selectedPostProcessor != null && selectedPostProcessor.getClass().getName().equals(reProcessorClassName))
            return selectedPostProcessor;
        else {
            selectedPostProcessor = null;
            for(DataPostProcessor reader : dataPostProcessorList)
                if(reader.getClass().getName().equals(reProcessorClassName))
                    selectedPostProcessor= reader;

            if(selectedPostProcessor == null)
                throw new Exception("Implementation not Found for Data Reader Process " + reProcessorClassName);

            return selectedPostProcessor;
        }
    }

    @Override
    public DataPostProcessorResponseDto postProcess(DataProcessorResponseDto processObject, ResultSetter setter, long startTime) throws Exception {
        return getPostProcessor().postProcess(processObject, setter, startTime);
    }
}
