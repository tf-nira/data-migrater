package io.mosip.packet.core.constant;

public enum ProcessorConstant {
    DATA_READER("mosip.migrator.data.reader.auto.selection.classname"),
    DATA_PROCESSOR("mosip.migrator.data.processor.auto.selection.classname"),
    DATA_EXPORTER("mosip.migrator.data.exporter.auto.selection.classname"),
    DATA_REPROCESSOR("mosip.migrator.data.reprocessor.auto.selection.classname");

    private String propertyName;

    ProcessorConstant(String propertyName) {
        this.propertyName = propertyName;
    }

    public String getProperty() {
        return propertyName;
    }
}
