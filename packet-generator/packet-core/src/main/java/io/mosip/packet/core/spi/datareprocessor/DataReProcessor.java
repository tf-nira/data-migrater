package io.mosip.packet.core.spi.datareprocessor;

import io.mosip.packet.core.constant.FieldCategory;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.service.thread.ResultSetter;

import java.util.HashMap;
import java.util.Map;

public interface DataReProcessor {
    public void reProcess() throws Exception;
    public void connectDataReader() throws Exception;
    public void disconnectDataReader();
}
