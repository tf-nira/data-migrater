package io.mosip.packet.core.util;

import com.google.gson.Gson;
import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.DBTypes;
import io.mosip.packet.core.constant.FieldCategory;
import io.mosip.packet.core.constant.GlobalConfig;
import io.mosip.packet.core.constant.QuerySelection;
import io.mosip.packet.core.constant.database.QueryLimitSetter;
import io.mosip.packet.core.constant.database.QueryOffsetSetter;
import io.mosip.packet.core.dto.dbimport.*;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.service.thread.CustomizedThreadPoolExecutor;
import io.mosip.packet.core.service.thread.ResultSetter;
import io.mosip.packet.core.service.thread.ThreadDBController;
import io.mosip.packet.core.service.thread.ThreadDBProcessor;
import io.mosip.packet.core.spi.datareader.DataReader;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

import static io.mosip.packet.core.constant.GlobalConfig.SESSION_KEY;
import static io.mosip.packet.core.constant.GlobalConfig.*;
import static io.mosip.packet.core.constant.RegistrationConstants.*;

@Component
public class DataBaseUtil implements DataReader {
    private static final Logger LOGGER = DataProcessLogger.getLogger(DataBaseUtil.class);
    private Connection conn = null;
    private boolean isTrackerSameHost = false;
    private String trackColumn = null;
    private DBTypes dbType = null;
    private Long OFFSET_VALUE = 0L;

    @Autowired
    private QueryFormatter formatter;

    @Autowired
    private DataMapperUtil dataMapperUtil;

    @Autowired
    private TrackerUtil trackerUtil;

    @Autowired
    private CommonUtil commonUtil;

    @Value("${mosip.packet.creater.max-threadpool-count:1}")
    private Integer dbReaderMaxThreadPoolCount;

    @Value("${mosip.packet.creater.max-records-process-per-threadpool:20000}")
    private Integer dbReaderMaxRecordsCountPerThreadPool;

    @Value("${mosip.packet.creater.db.max-thread-execution-count:10}")
    private Integer dbReaderMaxThreadExecCount;

    @Value("${mosip.extractor.application.id.column}")
    private String applicationIdColumn;

    private boolean oneTimeCheckForZeroOffset;

    CustomizedThreadPoolExecutor threadPool = null;

    private Map<String, DocumentValueMap> documentValue = new HashMap<>();


    public void connectDatabase(DBImportRequest dbImportRequest) throws SQLException {
        try {
            if(conn == null) {
                dbType = dbImportRequest.getDbType();
                Class driverClass = Class.forName(dbType.getDriver());
                DriverManager.registerDriver((Driver) driverClass.newInstance());
                String connectionHost = String.format(dbType.getDriverUrl(), dbImportRequest.getUrl(), dbImportRequest.getPort(), dbImportRequest.getDatabaseName());
                conn = DriverManager.getConnection(connectionHost, dbImportRequest.getUserId(), dbImportRequest.getPassword());

                if(isTrackerSameHost = trackerUtil.isTrackerHostSame(connectionHost, dbImportRequest.getDatabaseName()))
                    trackColumn = dbImportRequest.getTrackerInfo().getTrackerColumn();

                LOGGER.info("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "External DataBase" + dbImportRequest.getUrl() +  "Database Successfully connected");
                System.out.println("External DataBase " + dbImportRequest.getUrl() + " Successfully connected");
            }
        } catch (Exception e) {
            LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, " Error While Connecting Database " + ExceptionUtils.getStackTrace(e));
            System.exit(1);
        }

    }

    private void initializeDocumentMap(DBImportRequest dbImportRequest) {
        if(documentValue.isEmpty())
            for (FieldFormatRequest fieldFormatRequest : dbImportRequest.getColumnDetails()) {
                if(fieldFormatRequest.getDocumentAttributes() != null && fieldFormatRequest.getDocumentAttributes().getDocumentValueMap() != null) {
                    documentValue.put(fieldFormatRequest.getFieldToMap(), fieldFormatRequest.getDocumentAttributes().getDocumentValueMap());
                }
            }
    }

    private String generateQuery(TableRequestDto tableRequestDto, Map<FieldCategory, HashMap<String, Object>> dataMap, Map<String, HashMap<String, String>> fieldsCategoryMap) throws Exception {
        if (tableRequestDto.getQueryType().equals(QuerySelection.TABLE)) {
            String tableName = tableRequestDto.getTableNameWithOutSchema();
            List<String> ignoreFields = commonUtil.getNonIdSchemaNonTableFieldsMap();

            String columnNames = null;

            for(Map.Entry<String, String> entry : fieldsCategoryMap.get(tableName).entrySet()) {
                String column = entry.getKey();
                if(!ignoreFields.contains(column)) {
                    if(entry.getValue() != null)
                        column = "'"+entry.getValue()+"'" + " AS " + column;

                    if (columnNames == null)
                        columnNames = column;
                    else
                        columnNames += "," + column;
                }
            }

            if(fieldsCategoryMap.containsKey(DEFAULT_TABLE))
                for(Map.Entry<String, String> entry : fieldsCategoryMap.get(DEFAULT_TABLE).entrySet()) {
                    String column = entry.getKey();

                    if(!ignoreFields.contains(column)) {
                        if(entry.getValue() != null)
                            column = "'"+entry.getValue()+"'" + " AS " + column;

                        if (columnNames == null)
                            columnNames = column;
                        else
                            columnNames += "," + column;
                    }
                }

            String filterCondition = null;
            boolean whereCondition= false;

            String selectSql = "SELECT " + columnNames + "  from " + tableRequestDto.getTableName();

            if(tableRequestDto.getFilters() != null) {
                for (QueryFilter queryFilter : tableRequestDto.getFilters()) {
                    if (!whereCondition) {
                        filterCondition = " WHERE ";
                        whereCondition=true;
                    } else {
                        filterCondition += " AND ";
                    }

                    String condition1 = queryFilter.getFilterField() + " " + queryFilter.getFilterCondition().format(queryFilter.getFromValue(), queryFilter.getToValue(), queryFilter.getFieldType());

                    if(queryFilter.getConjunctionFilter() != null) {
                        condition1 = PrepareConjuctionQuery(queryFilter, condition1);
                    }

                    filterCondition +=condition1;
                }

                selectSql += filterCondition;
            }

            filterCondition = "";

            if(tableRequestDto.getExecutionOrderSequence().equals(1)) {
                if(isTrackerSameHost) {
                    if (!whereCondition) {
                        filterCondition = " WHERE ";
                        whereCondition=true;
                    } else {
                        filterCondition += " AND ";
                    }

                    filterCondition += trackColumn + String.format(" NOT IN (SELECT REF_ID FROM %s WHERE STATUS IN ('PROCESSED','PROCESSED_WITHOUT_UPLOAD', 'FAILED') AND SESSION_KEY = '%s') ", TRACKER_TABLE_NAME, SESSION_KEY);
                    selectSql += filterCondition;
                }

                if(applicationIdColumn != null && !applicationIdColumn.isEmpty())
                    selectSql += " ORDER BY  " + applicationIdColumn;

                if(!isTrackerSameHost && OFFSET_VALUE != null && OFFSET_VALUE > 0)
                    selectSql += " " + QueryOffsetSetter.valueOf(dbType.toString()).getValue(OFFSET_VALUE);

                selectSql += " " + QueryLimitSetter.valueOf(dbType.toString()).getValue(dbReaderMaxThreadPoolCount*dbReaderMaxRecordsCountPerThreadPool);
            }

            return formatter.replaceColumntoDataIfAny(selectSql, dataMap);
        } else if (tableRequestDto.getQueryType().equals(QuerySelection.SQL_QUERY)) {
            String sqlQuery = tableRequestDto.getSqlQuery().toUpperCase();
            return formatter.replaceColumntoDataIfAny(sqlQuery, dataMap);
        } else
            return null;
    }

    public void closeConnection() {
        if (conn != null) {
            try {
                conn.close();
                conn = null;
            } catch (SQLException e) {
                LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, " Error While Closing Database Connection " + e.getMessage());
            }
        }
    }

    public void populateDataFromResultSet(TableRequestDto tableRequestDto, List<FieldFormatRequest> columnDetails, Map<String, Object> resultData, Map<FieldCategory, HashMap<String, Object>> dataMap, Map<String, HashMap<String, String>> fieldsCategoryMap, Boolean localStoreRequired) throws Exception {
        if (dataMap != null && dataMap.size() <= 0) {
            dataMap.put(FieldCategory.DEMO, new HashMap<>());
            dataMap.put(FieldCategory.BIO, new HashMap<>());
            dataMap.put(FieldCategory.DOC, new HashMap<>());
        }

        for (FieldFormatRequest fieldFormatRequest : columnDetails) {
            dataMapperUtil.dataMapper(fieldFormatRequest, resultData, dataMap, tableRequestDto.getTableNameWithOutSchema().toUpperCase(), fieldsCategoryMap, localStoreRequired);
        }
    }

    public Map<String, Object> extractResultSet(ResultSet resultSet) throws SQLException {
        HashMap<String, Object> resultData = new HashMap<>();
        ResultSetMetaData metadata = resultSet.getMetaData();
        int columnCount = metadata.getColumnCount();
        for (int i = 1; i <= columnCount; i++) {
            resultData.put(metadata.getColumnName(i).toUpperCase(), null);
        }
        Map<String, Object> resultMap = (HashMap<String, Object>) resultData.clone();

        for (Map.Entry<String, Object> entry : resultData.entrySet()) {
            if(documentValue != null && !documentValue.isEmpty()) {
                for(Map.Entry<String, DocumentValueMap> documentEntry : documentValue.entrySet()) {
                    DocumentValueMap map = documentEntry.getValue();
                    try {
                        if(resultSet.getString(map.getColumnNameWithoutSchema()) != null) {
                            String value = resultSet.getString(map.getColumnNameWithoutSchema());
                            if(map.getMapColumnValue().equals(value)) {
                                resultMap.put(documentEntry.getKey() + "_" + entry.getKey(), resultSet.getObject(entry.getKey()));
                                resultMap.put(entry.getKey(), resultSet.getObject(entry.getKey()));
                                continue;
                            } else
                                resultMap.put(entry.getKey(), resultSet.getObject(entry.getKey()));
                        } else {
                            resultMap.put(entry.getKey(), resultSet.getObject(entry.getKey()));
                        }
                    } catch (Exception e) {
                        resultMap.put(entry.getKey(), resultSet.getObject(entry.getKey()));
                    }
                }
            } else {
                resultMap.put(entry.getKey(), resultSet.getObject(entry.getKey()));
            }
        }
        return resultMap;
    }

    private String PrepareConjuctionQuery(QueryFilter queryFilter, String mainCondition) throws Exception {
        String condition = "";
        String conjCondition = queryFilter.getConjunctionFilter().getConjuctionType().toString();

        for(QueryFilter queryFilter1 : queryFilter.getConjunctionFilter().getFilters()) {
            condition += " " + conjCondition + " ";

            condition += queryFilter1.getFilterField() + " " + queryFilter1.getFilterCondition().format(queryFilter1.getFromValue(), queryFilter1.getToValue(), queryFilter1.getFieldType());

            if(queryFilter1.getConjunctionFilter() != null) {
                String subCondition =PrepareConjuctionQuery(queryFilter1, condition);
                condition +=subCondition;

            }
        }

        return  "(" + mainCondition + " " + condition + ")";
    }

    @Override
    public void readData(DBImportRequest dbImportRequest, Map<FieldCategory, HashMap<String, Object>> dataHashMap, Map<String, HashMap<String, String>> fieldsCategoryMap, ResultSetter setter) throws Exception {
        TOTAL_RECORDS_FOR_PROCESS = 0l;

        try {
            if(conn != null) {
                IS_DATABASE_READ_OPERATION = true;
                initializeDocumentMap(dbImportRequest);
                oneTimeCheckForZeroOffset = true;
                threadPool = new CustomizedThreadPoolExecutor(dbReaderMaxThreadPoolCount, dbReaderMaxRecordsCountPerThreadPool, dbReaderMaxThreadExecCount, "DATABASE READER", true);

                Timer dataReader = new Timer("DataBase Reader");
                dataReader.schedule(new TimerTask() {
                    @SneakyThrows
                    @Override
                    public void run() {
                        PreparedStatement statement1 = null;
                        ResultSet scrollableResultSet = null;
                        try {
                            Float processPercentage = Float.valueOf((getPendingCountForProcess().floatValue() / Float.valueOf(dbReaderMaxThreadPoolCount * dbReaderMaxRecordsCountPerThreadPool)));
                            LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, " Database Reader Initial Condition for DB Read  ProcessPercentage, OFFSET_VALUE, OneTimeCheckForZeroOffset, CurrentPendingCount, PendingCountForProcess" +
                                    processPercentage, OFFSET_VALUE, oneTimeCheckForZeroOffset, threadPool.getCurrentPendingCount(), getPendingCountForProcess());

                            if ((processPercentage > 0.05 && processPercentage != 0) || (processPercentage == 0 && OFFSET_VALUE > 0 && oneTimeCheckForZeroOffset) || threadPool.getCurrentPendingCount() > 0) {
                            } else {
                                OFFSET_VALUE = trackerUtil.getDatabaseOffset() == null ? 0 : trackerUtil.getDatabaseOffset();
                                List<TableRequestDto> tableRequestDtoList = dbImportRequest.getTableDetails();
                                Collections.sort(tableRequestDtoList);
                                TableRequestDto tableRequestDto = tableRequestDtoList.get(0);
                                statement1 = conn.prepareStatement(generateQuery(tableRequestDto, dataHashMap, fieldsCategoryMap), ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                                scrollableResultSet = statement1.executeQuery();

                                if(scrollableResultSet.last()) {
                                    TOTAL_RECORDS_FOR_PROCESS += Long.valueOf(scrollableResultSet.getRow());
                                    OFFSET_VALUE += Long.valueOf(scrollableResultSet.getRow());
                                    trackerUtil.updateDatabaseOffset(OFFSET_VALUE);
                                }

                                if (scrollableResultSet.getRow() <= 0) {
                                    LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, "Cancelling Database Reader since No Data" + scrollableResultSet.getFetchSize());
                                    dataReader.cancel();
                                    threadPool.setInputProcessCompleted(true);
                                    IS_DATABASE_READ_OPERATION = false;
                                }

                                scrollableResultSet.beforeFirst();

                                if (scrollableResultSet != null) {
                                    while (scrollableResultSet.next()) {
                                        try {
                                            Map<String, Object> resultData = extractResultSet(scrollableResultSet);
                                            ThreadDBController baseDbThreadController = new ThreadDBController();
                                            baseDbThreadController.setSetter(setter);
                                            baseDbThreadController.setResultMap(resultData);
                                            baseDbThreadController.setProcessor(new ThreadDBProcessor() {
                                                @Override
                                                public void processData(ResultSetter setter, Map<String, Object> resultMap) throws Exception {
                                                    Map<FieldCategory, HashMap<String, Object>> dataHashMap = new HashMap<>();
                                                    populateDataFromResultSet(tableRequestDto, dbImportRequest.getColumnDetails(), resultMap, dataHashMap, fieldsCategoryMap, false);

                                                    if (!trackerUtil.isRecordPresent(dataHashMap.get(FieldCategory.DEMO).get(dbImportRequest.getTrackerInfo().getTrackerColumn()), GlobalConfig.getActivityName())) {
                                                        for (int i = 1; i < tableRequestDtoList.size(); i++) {
                                                            PreparedStatement statement2 = null;
                                                            ResultSet resultSet1 = null;
                                                            try {
                                                                TableRequestDto tableRequestDto1 = tableRequestDtoList.get(i);
                                                                statement2 = conn.prepareStatement(generateQuery(tableRequestDto1, dataHashMap, fieldsCategoryMap), ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
                                                                resultSet1 = statement2.executeQuery();

                                                                Map<String, Object> resultData1 = new HashMap<>();
                                                                while (resultSet1 != null && resultSet1.next()) {
                                                                    resultData1.putAll(extractResultSet(resultSet1));
                                                                }

                                                                if(resultData1 != null)
                                                                    populateDataFromResultSet(tableRequestDto1, dbImportRequest.getColumnDetails(), resultData1, dataHashMap, fieldsCategoryMap, false);
                                                            } finally {
                                                                if (resultSet1 != null)
                                                                    resultSet1.close();

                                                                if (statement2 != null)
                                                                    statement2.close();
                                                            }
                                                        }
                                                        setter.setResult(dataHashMap);
                                                    } else {
                                                        LOGGER.debug("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, " Record Already Processed for ref_id" + dataHashMap.get(FieldCategory.DEMO).get(dbImportRequest.getTrackerInfo().getTrackerColumn()));
                                                    }
                                                }
                                            });
                                            threadPool.ExecuteTask(baseDbThreadController);
                                        } catch (Exception e) {
                                            threadPool.increaseFailedRecordCount();
                                            LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, " Error While Extracting Data " + (new Gson()).toJson(dataHashMap) + " Stack Trace : " + ExceptionUtils.getStackTrace(e));
                                        }
                                    }
                                }
                                oneTimeCheckForZeroOffset = false;
                            }
                        } catch (Exception e) {
                            LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID, " Error While Extracting Data " + (new Gson()).toJson(dataHashMap) + " Stack Trace : " + ExceptionUtils.getStackTrace(e));
                            throw e;
                        } finally {
                            if(scrollableResultSet != null)
                                scrollableResultSet.close();

                            if(statement1 != null)
                                statement1.close();
                        }
                    }
                }, 0,  70000L);
            } else
                throw new SQLException("Unable to Connect With Database. Please check the Configuration");
        } catch(Exception e) {
            if(threadPool != null)
                threadPool.increaseFailedRecordCount();
            throw e;
        }
    }

    @Override
    public void connectDataReader(DBImportRequest dbImportRequest) throws Exception {
        connectDatabase(dbImportRequest);
    }

    @Override
    public void disconnectDataReader() {
        closeConnection();
    }
}
