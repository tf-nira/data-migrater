package io.mosip.packet.core.constant;

public enum ReferenceClassName {
    DATABASE_READER(ProcessorConstant.DATA_READER, "io.mosip.packet.core.util.DataBaseUtil"),
    CSV_READER(ProcessorConstant.DATA_READER, null),
    JSON_READER(ProcessorConstant.DATA_READER, null),
    MOSIP_PACKET_UPLOAD(ProcessorConstant.DATA_EXPORTER, "io.mosip.packet.data.dataexporter.MosipPacketExporter"),
    MOSIP_IDREPO_UPLOAD(ProcessorConstant.DATA_EXPORTER, "io.mosip.packet.data.IdrepoUploader");

    private String className;
    private ProcessorConstant processname;

    ReferenceClassName(ProcessorConstant processname, String className) {
        this.className = className;
        this.processname = processname;
    }

    public ProcessorConstant getProcess() {
        return processname;
    }

    public String getClassName() {
        return className == null ? null : className;
    }
}
