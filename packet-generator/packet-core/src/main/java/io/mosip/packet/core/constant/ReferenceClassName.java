package io.mosip.packet.core.constant;

public enum ReferenceClassName {
    DATABASE_READER(ProcessorConstant.DATA_READER, "io.mosip.packet.data.datareader.DataBaseUtil"),
    CSV_READER(ProcessorConstant.DATA_READER, null),
    JSON_READER(ProcessorConstant.DATA_READER, null),
    MOSIP_PACKET_UPLOAD(ProcessorConstant.DATA_EXPORTER, "io.mosip.packet.data.dataexporter.MosipPacketExporter"),
    MOSIP_PACKET_REPROCESSOR(ProcessorConstant.DATA_REPROCESSOR, "io.mosip.packet.data.datareprocessor.MosipPacketReprocessor"),
    MOSIP_IDREPO_UPLOAD(ProcessorConstant.DATA_POST_PROCESSOR, "io.mosip.packet.data.IdrepoUploader"),
    MOSIP_PACKET_DTO_GENERATOR(ProcessorConstant.DATA_PROCESSOR, "io.mosip.packet.data.datapostprocessor.MosipPacketDTOProcessor"),
    MOSIP_PACKET_POST_PROCESSOR(ProcessorConstant.DATA_POST_PROCESSOR, "io.mosip.packet.data.datapostprocessor.MOSIPPostProcessor");

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
