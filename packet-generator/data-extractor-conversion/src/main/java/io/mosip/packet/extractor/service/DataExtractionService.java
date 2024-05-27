package io.mosip.packet.extractor.service;

import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import io.mosip.packet.core.dto.dbimport.PacketCreatorResponse;
import io.mosip.packet.core.dto.packet.RegistrationIdRequest;

import java.io.IOException;
import java.sql.SQLException;
import java.util.HashMap;

public interface DataExtractionService {

    public HashMap<String, Object> extractBioDataFromDB(DBImportRequest dbImportRequest, Boolean localStoreRequired) throws Exception;
    public HashMap<String, Object> extractBioDataFromDBAsBytes(DBImportRequest dbImportRequest, Boolean localStoreRequired) throws Exception;
    public PacketCreatorResponse createPacketFromDataBase(DBImportRequest dbImportRequest) throws SQLException, IOException, Exception;
    public String refreshQualityAnalysisData() throws Exception;
    public String extractBioDataFromPacket(RegistrationIdRequest registrationIdRequest) throws Exception;
}
