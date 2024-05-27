package io.mosip.packet.core.constant;

import lombok.Getter;

@Getter
public enum BioSubType {
    LEFT_IRIS("leftEye", "Left"),
    RIGHT_IRIS("rightEye", "Right"),

    RIGHT_INDEX("rightIndex", "Right IndexFinger"),
    RIGHT_MIDDLE("rightMiddle", "Right MiddleFinger"),
    RIGHT_RING("rightRing","Right RingFinger"),
    RIGHT_LITTLE("rightLittle", "Right LittleFinger"),

    LEFT_INDEX("leftIndex", "Left IndexFinger"),
    LEFT_MIDDLE("leftMiddle", "Left MiddleFinger"),
    LEFT_RING("leftRing","Left RingFinger"),
    LEFT_LITTLE("leftLittle", "Left LittleFinger"),

    LEFT_THUMB("leftThumb", "Left Thumb"),
    RIGHT_THUMB("rightThumb", "Right Thumb"),

    FACE("face", ""),

    UNKNOWN("UNKNOWN", "UNKNOWN");

    private String bioAttribute;
    private String bioSubType;

    BioSubType(String bioAttribute, String bioSubType) {
        this.bioAttribute = bioAttribute;
        this.bioSubType = bioSubType;
    }

    public static BioSubType getBioSubType(String bioAttribute) {
        for(BioSubType subType : BioSubType.values())
            if(subType.bioAttribute.equals(bioAttribute))
                return subType;
        return BioSubType.UNKNOWN;
    }

    public static BioSubType getBioAttribute(String bioSubType) {
        for(BioSubType subType : BioSubType.values())
            if(subType.bioSubType.equals(bioSubType))
                return subType;
        return BioSubType.UNKNOWN;
    }
}
