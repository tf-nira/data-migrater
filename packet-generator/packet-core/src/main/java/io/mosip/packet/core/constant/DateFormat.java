package io.mosip.packet.core.constant;

import lombok.Getter;

@Getter
public enum DateFormat {
    DMY_WITH_HYPEN("dd-MM-yyyy"),
    DMMY_WITH_HYPEN("dd-MMM-yyyy"),
    YMD_WITH_HYPEN("yyyy-MM-dd"),
    YMD_WITH_HYPEN_TIME_MILLISECOND("yyyy-MM-dd HH:mm:ss.S");

    private final String format;

    DateFormat(String format) {
       this.format = format;
    }
}
