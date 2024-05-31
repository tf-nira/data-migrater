package io.mosip.packet.client.controller.popup;

import io.mosip.packet.client.dto.ColumnData;
import io.mosip.packet.client.dto.FilterTableViewData;
import io.mosip.packet.client.model.ApplicationResourceContext;
import io.mosip.packet.client.mvp.BaseController;
import io.mosip.packet.core.constant.ApiName;
import io.mosip.packet.core.constant.FieldType;
import io.mosip.packet.core.constant.FilterCondition;
import io.mosip.packet.core.dto.RequestWrapper;
import io.mosip.packet.core.dto.ResponseWrapper;
import io.mosip.packet.core.dto.dbimport.DBImportRequest;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;

@Component
public class FilterAddPopupController implements Initializable {
    private FilterTableViewData data;
    private String tableName;
    private HashMap<String, ColumnData> columnMap = new HashMap<>();

    @FXML
    private ComboBox columnName;

    @FXML
    private ComboBox columnType;

    @FXML
    private ComboBox condition;

    @FXML
    private TextField fromValue;

    @FXML
    private TextField toValue;

//    @Autowired
//    private /FilterValidation filterValidation;

    private RestTemplate restTemplate = new RestTemplate();

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        columnType.getItems().addAll(FXCollections.observableArrayList(FieldType.values()));
        condition.getItems().addAll(FXCollections.observableArrayList(FilterCondition.values()));

        Environment env = ApplicationResourceContext.getInstance().getApplicationContext().getEnvironment();
        RequestWrapper<DBImportRequest> request = new RequestWrapper<>();
        DBImportRequest dbImportRequest = BaseController.getDbImportRequest();
        request.setRequest(dbImportRequest);

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(env.getProperty(ApiName.FETCH_COLUMN_LIST.toString()));
        UriComponents url = builder.buildAndExpand(tableName);
        ResponseWrapper response= (ResponseWrapper) restTemplate.postForObject(url.toUri(), request, ResponseWrapper.class);
        List<Map<String, Object>> tableListResponse = ( List<Map<String, Object>>) response.getResponse();

        for(Map<String, Object> map : tableListResponse) {
            ColumnData data = new ColumnData();
            data.setSchema((String) map.get("TABLE_SCHEMA"));
            data.setTableName((String) map.get("TABLE_NAME"));
            data.setColumnName((String) map.get("COLUMN_NAME"));
            data.setDataType(FieldType.getFieldType((String) map.get("DATA_TYPE")));
            data.setLength((String) map.get("LENGTH"));
            columnMap.put((String) map.get("COLUMN_NAME"), data);
        }

        columnName.getItems().addAll(FXCollections.observableArrayList(tableListResponse.stream().map(e -> e.get("COLUMN_NAME")).collect(Collectors.toList())));

        columnName.valueProperty().addListener(new ChangeListener() {
            @Override
            public void changed(ObservableValue observable, Object oldValue, Object newValue) {
                if(newValue != null) {
                    ColumnData data = columnMap.get(newValue);
                    columnType.setValue(data.getDataType());
                }
            }
        });

        condition.valueProperty().addListener(new ChangeListener() {
            @Override
            public void changed(ObservableValue observable, Object oldValue, Object newValue) {
                if(newValue != null) {
                    fromValue.setText(null);
                    fromValue.setEditable(false);
                    toValue.setText(null);
                    toValue.setEditable(false);

                    switch (FilterCondition.valueOf(newValue.toString())) {
                        case EQUAL:
                        case GREATER_THEN:
                        case LESS_THEN:
                        case LESS_THEN_AND_EQUAL:
                        case GREATER_THEN_AND_EQUAL:
                            fromValue.setEditable(true);
                            break;
                        case BETWEEN:
                            fromValue.setEditable(true);
                            toValue.setEditable(true);
                            break;
                    }
                }
            }
        });

    }

    public void setModel(FilterTableViewData data, String tableName) {
        this.data = data;
        this.tableName = tableName;
    }

    @FXML
    public void saveColumn(ActionEvent event) {
        System.out.println("Save Column Method Entering");
    }
}
