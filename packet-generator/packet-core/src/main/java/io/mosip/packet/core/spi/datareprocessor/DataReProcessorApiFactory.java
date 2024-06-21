package io.mosip.packet.core.spi.datareprocessor;

import io.mosip.packet.core.constant.FieldCategory;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.service.thread.ResultSetter;

import java.util.HashMap;
import java.util.Map;

public interface DataReProcessorApiFactory {
    public void reProcess() throws Exception;
    public void connectDataReader(DBImportRequest dbImportRequest) throws Exception;
    public void disconnectDataReader() throws Exception;
}
