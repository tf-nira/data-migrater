package io.mosip.packet.core.util;

import io.mosip.packet.core.constant.FieldCategory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class QueryFormatter {
    public String replaceColumntoDataIfAny(String query, Map<FieldCategory, HashMap<String, Object>> dataMap) throws Exception {
        if(dataMap != null) {
            do {
                int startIndex = query.indexOf("${", 0);
                int endIndex = query.indexOf("}", 0);

                if (endIndex > 0) {
                    String columnText = query.substring(startIndex, endIndex+1).replace("${", "").replace("}", "");
                    FieldCategory category = FieldCategory.valueOf(columnText.split(":")[0]);
                    String column = columnText.split(":")[1];

                    Object val = dataMap.get(category).get(column);

                    if (val == null)
                        throw  new Exception("Missing Value in " + category.toString() + "category for the column " + column);

                    query = query.replace("'${" + columnText + "}'", String.valueOf("'" + val +"'"));
                    query = query.replace("${" + columnText + "}", String.valueOf("'" + val +"'"));
                }
            } while(query.contains("${"));
        }

        return query;
    }

    public String queryFormatter(String query, Map<String, String> map) {
        for(Map.Entry<String, String> entry : map.entrySet()) {
            query = query.replace("<" + entry.getKey() + ">", (entry.getValue() == null ? "" : entry.getValue()));
        }
        return query;
    }
}
