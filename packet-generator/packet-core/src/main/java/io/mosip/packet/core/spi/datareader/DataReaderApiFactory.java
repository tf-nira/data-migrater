package io.mosip.packet.core.spi.datareader;

import io.mosip.packet.core.constant.FieldCategory;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.service.thread.ResultSetter;

import java.util.HashMap;
import java.util.Map;

public interface DataReaderApiFactory {
    public void readData(DBImportRequest dbImportRequest, Map<FieldCategory, HashMap<String, Object>> dataHashMap, Map<String, HashMap<String, String>> fieldsCategoryMap, ResultSetter setter) throws Exception;
    public Map<FieldCategory, HashMap<String, Object>> readDataOnDemand(DBImportRequest dbImportRequest, Map<FieldCategory, HashMap<String, Object>> dataHashMap, Map<String, HashMap<String, String>> fieldsCategoryMap, boolean isPacketCreationProcess, boolean isPacketProcessed) throws Exception;
    public void connectDataReader(DBImportRequest dbImportRequest) throws Exception;
    public void disconnectDataReader() throws Exception;
}
