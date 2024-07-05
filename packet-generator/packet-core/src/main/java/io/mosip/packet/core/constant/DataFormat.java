package io.mosip.packet.core.constant;

public enum DataFormat {
    JPEG("jpg", "jpg"),
    JP2("jpeg 2000", "jp2"),
    WSQ("wsq","wsq"),
    ISO("ISO", "iso"),
    PNG("png","png"),
    YMD("yyyy/MM/dd", "yyyy/MM/dd"),
    DMY("dd/MM/yyyy", "dd/MM/yyyy"),
    DMY_WITH_HYPEN("dd-MM-yyyy", "dd-MM-yyyy"),
    YMD_WITH_HYPEN("yyyy-MM-dd", "yyyy-MM-dd"),
    YMD_WITH_HYPEN_TIME_MILLISECOND("yyyy-MM-dd HH:mm:ss.S", "yyyy-MM-dd HH:mm:ss.S");


    public final String format;
    public final String fileFormat;

    DataFormat(final String format, final String fileFormat) {
        this.format = format;
        this.fileFormat = fileFormat;
    }

    public String getFormat() {
        return format;
    }

    public String getFileFormat() {
        return fileFormat;
    }
}
