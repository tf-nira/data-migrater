package io.mosip.packet.data.qualityscore.writer;

import io.mosip.kernel.core.exception.ExceptionUtils;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.DBTypes;
import io.mosip.packet.core.constant.tracker.NumberType;
import io.mosip.packet.core.constant.tracker.StringType;
import io.mosip.packet.core.constant.tracker.TimeStampType;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.spi.QualityWriterFactory;
import io.mosip.packet.core.util.CommonUtil;
import io.mosip.packet.core.util.QueryFormatter;
import io.mosip.packet.data.qualityscore.writer.constant.TableWriterConstant;
import io.mosip.packet.data.qualityscore.writer.constant.TableWriterQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.sql.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

import static io.mosip.packet.core.constant.GlobalConfig.IS_RUNNING_AS_BATCH;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_ID;
import static io.mosip.packet.core.constant.RegistrationConstants.APPLICATION_NAME;

@Component
@ConditionalOnProperty(value = "mosip.data.quality.writer.classname", havingValue = "TableWriter")
public class TableWriter implements QualityWriterFactory {
    private static final Logger LOGGER = DataProcessLogger.getLogger(TableWriter.class);
    private static Connection conn = null;
    private static String connectionHost = null;
    private static String WRITER_TABLE_NAME = "TB_T_QUALITY_SCORE";
    private static String ANALYZER_TABLE_NAME = "TB_T_QUALITY_ANALYSIS";
    private static LinkedHashMap<String, String> map = new LinkedHashMap<>();
    private static LinkedHashMap<String, String> consolidateMap = new LinkedHashMap<>();
    private static HashMap<String, Class> columnMap = new HashMap<>();
    private static boolean initialLoad = true;

    @Autowired
    private CommonUtil commonUtil;

    @Autowired
    private TableWriterQuery tableWriterQuery;

    @Autowired
    private Environment env;

    @Autowired
    private QueryFormatter queryFormatter;

    @Value("${mosip.tablewriter.clear.table.required:true}")
    private boolean tableClearRequired;

    @PostConstruct
    public void initialize(){
        columnMap.put("REF_ID", String.class);
        columnMap.put("REG_NO", String.class);

        try {
            IS_RUNNING_AS_BATCH = env.getProperty("mosip.packet.creator.run.as.batch.execution") == null ? false : Boolean.valueOf(env.getProperty("mosip.packet.creator.run.as.batch.execution"));

            if(conn == null) {
                DBTypes dbType = Enum.valueOf(DBTypes.class, env.getProperty("spring.datasource.tracker.dbtype"));

                Class driverClass = Class.forName(dbType.getDriver());
                DriverManager.registerDriver((Driver) driverClass.newInstance());
                connectionHost = String.format(dbType.getDriverUrl(), env.getProperty("spring.datasource.tracker.host"), env.getProperty("spring.datasource.tracker.port"), env.getProperty("spring.datasource.tracker.database"));
                conn = DriverManager.getConnection(connectionHost, env.getProperty("spring.datasource.tracker.username"), env.getProperty("spring.datasource.tracker.password"));
                conn.setAutoCommit(true);

                Statement statement = null;
                try {
                    try {
                        statement = conn.createStatement();
                        boolean ifTablePresent = statement.execute("SELECT COUNT(*) FROM " + WRITER_TABLE_NAME);

                        if(ifTablePresent) {
//                            System.out.println("Table : " + WRITER_TABLE_NAME +  " Do you want to clear Table ? Y-Yes, N-No");
//                            String option ="";
//                            if(!IS_RUNNING_AS_BATCH) {
//                                Scanner scanner = new Scanner(System.in);
//                                option = scanner.next();
//                            } else if(tableClearRequired) {
//                                option = "Y";
//                            }
                        	String option ="";
                            if(tableClearRequired) {
                                option = "Y";
                            } else {
                            	option = "N";
                            }

                            if(option.equalsIgnoreCase("y")) {
                                statement.execute("TRUNCATE TABLE " + WRITER_TABLE_NAME);
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Table " + WRITER_TABLE_NAME +  " not Present in DB " + env.getProperty("spring.datasource.tracker.host") +  ". Do you want to create ? Y-Yes, N-No");
                        String option ="";
                        if(!IS_RUNNING_AS_BATCH) {
                            Scanner scanner = new Scanner(System.in);
                            option = scanner.next();
                        } else {
                            option = "Y";
                        }

                        if(option.equalsIgnoreCase("y")) {
                            try {
                                TableWriter.DBCreator dbCreator = new TableWriter.DBCreator(dbType, WRITER_TABLE_NAME);
                                String[] scripts = dbCreator.getValue().split(";");
                                for(String script : scripts)
                                    statement.execute(script);


                            } catch (Exception e1) {
                                LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID,
                                        "Exception encountered during Table Creation - TableWriter  "
                                                + ExceptionUtils.getStackTrace(e1));
                                System.exit(1);
                            }
                        } else {
                            System.exit(1);
                        }
                    }

                    try {
                        statement.execute("DROP TABLE " + ANALYZER_TABLE_NAME);
                    } catch (Exception e){
                    }

                    TableWriter.DBCreator dbCreator = new TableWriter.DBCreator(dbType, ANALYZER_TABLE_NAME);
                    String[] scripts = dbCreator.getValue().split(";");
                    for(String script : scripts)
                        statement.execute(script);
                } finally {
                    if(statement != null)
                        statement.close();
                }
            }
        } catch (Exception e) {
            LOGGER.error("SESSION_ID", APPLICATION_NAME, APPLICATION_ID,
                    "Exception encountered during context initialization - TrackerUtil "
                            + ExceptionUtils.getStackTrace(e));
        }
    }

    @Override
    public synchronized HashMap<String, String> getDataMap() throws Exception {
        Statement statement = null;

        try {
            if(initialLoad) {
                statement = conn.createStatement();
                TableWriter.DBCreator dbCreator = new TableWriter.DBCreator();
                DBTypes dbType = Enum.valueOf(DBTypes.class, env.getProperty("spring.datasource.tracker.dbtype"));

                for(Object ob : commonUtil.getBioAttributesforAll()) {
                    try {
                        statement.execute("ALTER TABLE " + ANALYZER_TABLE_NAME + " ADD " + dbCreator.addColumn(ob.toString().toUpperCase(), Number.class, 6,0, false, dbType));
                        statement.execute("ALTER TABLE " + WRITER_TABLE_NAME + " ADD " + dbCreator.addColumn(ob.toString().toUpperCase(), Number.class, 5,2, false, dbType));
                    }catch (Exception e){
                    }
                    columnMap.put(ob.toString().toUpperCase(), Number.class);
                }

                initialLoad=false;
            }
        } finally {
            if(statement != null)
                statement.close();
        }


        if(map.size() <= 2) {
            map.put("ref_id", null);
            map.put("reg_no", null);

            for(Object ob : commonUtil.getBioAttributesforAll())
                if(!map.containsKey(ob.toString()))
                    map.put(ob.toString(), null);

            consolidateMap.putAll(map);
        }

        return (HashMap) map.clone();
    }

    @Override
    public synchronized void writeQualityData(HashMap<String, String> csvMap) throws Exception {
        Statement statement = conn.createStatement();
        PreparedStatement preparedStatement = null;
        DBTypes dbType = Enum.valueOf(DBTypes.class, env.getProperty("spring.datasource.tracker.dbtype"));

        try {
            if(!map.keySet().containsAll(csvMap.keySet())) {
                TableWriter.DBCreator dbCreator = new TableWriter.DBCreator();

                for(String key : csvMap.keySet())
                    if(!map.containsKey(key)) {
                        try {
                            statement.execute("ALTER TABLE " + WRITER_TABLE_NAME + " ADD " + dbCreator.addColumn(key.toUpperCase(), String.class, 4000, 0, false, dbType));
                        } catch (Exception e){
                        }
                        columnMap.put(key.toString().toUpperCase(), String.class);
                        map.put(key, null);
                    }
            }

            String columns = "";
            String values = "";
            String updateField = "";

            for(String key : map.keySet()) {
                if(!key.toLowerCase().contains("ref_id")) {
                    if(columns.isEmpty())
                        columns = key;
                    else
                        columns += ", " + key;

                    if(values.isEmpty())
                        values = "?";
                    else
                        values += ", ?";

                    if(updateField.isEmpty())
                        updateField = key + " = ?" ;
                    else
                        updateField += ", " + key + " = ?";
                }
            }
            String query = TableWriterQuery.getInsertQueries(WRITER_TABLE_NAME).get(dbType);
            Map<String, String> valueMap = new HashMap<>();
            valueMap.put("TABLE_NAME", WRITER_TABLE_NAME);
            valueMap.put("REF_ID", csvMap.get("ref_id"));
            valueMap.put("VALUES", values);
            valueMap.put("COLUMN_NAME", columns);
            valueMap.put("UPDATE_COLUMN_NAME", updateField);

            Boolean updateColumnPresent = query.contains("<UPDATE_COLUMN_NAME>");
            preparedStatement = conn.prepareStatement(queryFormatter.queryFormatter(query, valueMap));

            int i = 0;
            for(String key : map.keySet()) {
                if(!key.toLowerCase().contains("ref_id")) {
                    i++;
                    switch(columnMap.get(key.toUpperCase()).getSimpleName()) {
                        case "String" :
                            preparedStatement.setString(i, csvMap.get(key));
                            if(updateColumnPresent)
                                preparedStatement.setString(map.size() + i-1, csvMap.get(key));
                            break;
                        case "Number" :
                            if(csvMap.get(key) == null) {
                                preparedStatement.setNull(i, Types.FLOAT);
                                if(updateColumnPresent)
                                    preparedStatement.setNull(map.size() + i-1, Types.FLOAT);
                            } else {
                                preparedStatement.setFloat(i, Float.valueOf(csvMap.get(key)));
                                if(updateColumnPresent)
                                    preparedStatement.setFloat(map.size() + i-1, Float.valueOf(csvMap.get(key)));
                            }
                            break;
                    }
                }
            }

            preparedStatement.execute();
            csvMap.clear();
        } finally {
            if(statement != null)
                statement.close();

            if(preparedStatement != null)
                preparedStatement.close();
        }
    }

    private static class DBCreator {

        private StringBuilder sb;

        public DBCreator(){};

        public DBCreator(DBTypes dbTypes, String TableName) throws Exception {
            sb = new StringBuilder();
            sb.append(String.format("CREATE TABLE %s (", TableName));
            sb.append(addColumn("REF_ID", String.class, 100, 0, true, dbTypes) + ",");
            sb.append(addColumn("REG_NO", String.class, 100, 0, false, dbTypes));

            sb.append(");");

            sb.append(String.format("ALTER TABLE %s ADD PRIMARY KEY (REF_ID);", TableName));
        }

        private String addColumn(String columnName, Class columnType, Integer length, Integer precision, boolean isNotNull, DBTypes dbTypes) throws Exception {
            switch (columnType.getSimpleName()) {
                case "String" :
                    return columnName + " " + StringType.valueOf(dbTypes.toString()).getValue(length.toString()) + (isNotNull ? " NOT NULL" : "");
                case "Number" :
                    return columnName + " " + NumberType.valueOf(dbTypes.toString()).getValue(length.toString(), precision.toString()) + (isNotNull ? " NOT NULL" : "");
                case "Timestamp" :
                    return columnName + " " + TimeStampType.valueOf(dbTypes.toString()).getType() + (isNotNull ? " NOT NULL" : "");
                default:
                    throw new Exception("Column Type " + columnType.getSimpleName() + " Not Found");
            }
        }

        public String getValue() {
            return sb.toString();
        }

    }

    @Override
    public void preDestroyProcess() throws SQLException {
        Statement statement = null;
        PreparedStatement preparedStatement = null;
        try {
            if(consolidateMap == null || consolidateMap.isEmpty()) {
                consolidateMap = new LinkedHashMap<>();

                for(Object ob : commonUtil.getBioAttributesforAll()) {
                    consolidateMap.put(ob.toString(), null);
                }
            }

            if(columnMap == null || (!columnMap.isEmpty() && columnMap.size() <= 2)) {
                getDataMap();
            }

            for(Map.Entry<String, String> queryEntry : TableWriterQuery.getQueries(TableWriterConstant.ROW_ANALYSER_QUERIES).entrySet()) {
                statement = conn.createStatement();
                LinkedHashMap<String, String> objectMap = (LinkedHashMap<String, String>) consolidateMap.clone();
                objectMap.put("ref_id", queryEntry.getKey());

                for(Map.Entry<String, String> entry : objectMap.entrySet()) {
                    if(!entry.getKey().equalsIgnoreCase("ref_id") && !entry.getKey().equalsIgnoreCase("reg_no")) {
                        String query = queryEntry.getValue().replace("<$TABLE_NAME>", WRITER_TABLE_NAME).replace("<$COLUMN_NAME>", entry.getKey());
                        ResultSet resultSet = statement.executeQuery(query);
                        if(resultSet.next())
                            objectMap.put(entry.getKey(), resultSet.getString(1));
                        resultSet.close();
                    }
                }

                String columns = "";
                String values = "";

                for(String key : objectMap.keySet()) {
                    if(columns.isEmpty())
                        columns = key;
                    else
                        columns += ", " + key;

                    if(values.isEmpty())
                        values = "?";
                    else
                        values += ", ?";
                }

                preparedStatement = conn.prepareStatement("INSERT INTO " + ANALYZER_TABLE_NAME + " (" + columns + ") VALUES (" + values + ")");

                int i = 0;
                for(String key : objectMap.keySet()) {
                    i++;
                    switch(columnMap.get(key.toUpperCase()).getSimpleName()) {
                        case "String" :
                            preparedStatement.setString(i, objectMap.get(key));
                            break;
                        case "Number" :
                            if(objectMap.get(key) == null)
                                preparedStatement.setNull(i, Types.INTEGER);
                            else
                                preparedStatement.setInt(i, Float.valueOf(objectMap.get(key)).intValue());
                            break;
                    }
                }

                preparedStatement.execute();
                preparedStatement.close();
                statement.close();
            }
        } catch (Exception e) {
            LOGGER.error("Error While Processing Quality Data into Analyzer Table " + ANALYZER_TABLE_NAME + " " + e.getMessage());
        } finally {
            if(statement != null)
                statement.close();

            if(preparedStatement != null)
                preparedStatement.close();
        }
    }
}
