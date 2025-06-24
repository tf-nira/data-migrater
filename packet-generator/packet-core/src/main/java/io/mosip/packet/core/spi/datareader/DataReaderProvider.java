package io.mosip.packet.core.spi.datareader;

import io.mosip.packet.core.constant.FieldCategory;
import io.mosip.packet.core.dto.BooleanWrapper;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.service.thread.ResultSetter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DataReaderProvider implements DataReaderApiFactory {
    @Autowired
    List<DataReader> dataReaderList;

    private DataReader selectedReader;

    @Autowired
    Environment env;

    private DataReader getReader() throws Exception {
        String readerClassName = env.getProperty("mosip.migrator.data.reader.auto.selection.classname");
        if(selectedReader != null && selectedReader.getClass().getName().equals(readerClassName))
            return selectedReader;
        else {
            selectedReader = null;
            for(DataReader reader : dataReaderList)
                if(reader.getClass().getName().equals(readerClassName))
                    selectedReader= reader;

            if(selectedReader == null)
                throw new Exception("Implementation not Found for Data Reader Process " + readerClassName);
            return selectedReader;
        }
    }

    @Override
    public void readData(DBImportRequest dbImportRequest, Map<FieldCategory, HashMap<String, Object>> dataHashMap, Map<String, HashMap<String, String>> fieldsCategoryMap, ResultSetter setter) throws Exception {
        getReader().readData(dbImportRequest, dataHashMap, fieldsCategoryMap, setter);
    }
    
    @Override
    public Map<FieldCategory, HashMap<String, Object>> readDataOnDemand(DBImportRequest dbImportRequest, Map<FieldCategory, HashMap<String, Object>> dataHashMap, Map<String, HashMap<String, String>> fieldsCategoryMap, BooleanWrapper isPacketProcessed, boolean isPacketCreationProcess, String nin) throws Exception {
        return getReader().readDataOnDemand(dbImportRequest, dataHashMap, fieldsCategoryMap, isPacketProcessed, isPacketCreationProcess, nin);
    }

    @Override
    public void connectDataReader(DBImportRequest dbImportRequest) throws Exception {
        getReader().connectDataReader(dbImportRequest);
    }

    @Override
    public void disconnectDataReader() throws Exception {
        getReader().disconnectDataReader();
    }
    
    @Override
    public void setupDatabase(DBImportRequest dbImportRequest) throws Exception {
        getReader().setupDatabase(dbImportRequest);
    }
}
