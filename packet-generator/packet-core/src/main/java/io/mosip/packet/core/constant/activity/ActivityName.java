package io.mosip.packet.core.constant.activity;

import io.mosip.packet.core.constant.ReferenceClassName;

public enum ActivityName {
    DATA_EXPORTER("DATA UPLOADER", new ActivityReferenceClassMapping(ReferenceClassName.MOSIP_PACKET_UPLOAD), null),
    DATA_REPROCESSOR("DATA REPROCESSOR", new ActivityReferenceClassMapping(ReferenceClassName.MOSIP_PACKET_REPROCESSOR),  new ActivityName[]{DATA_EXPORTER}),
    DATA_PROCESSOR("DATA PROCESSOR", new ActivityReferenceClassMapping(ReferenceClassName.MOSIP_PACKET_DTO_GENERATOR),  null),
    DATA_POST_PROCESSOR("DATA POST PROCESSOR", new ActivityReferenceClassMapping(ReferenceClassName.MOSIP_PACKET_POST_PROCESSOR),  null),
    DATA_CREATOR("DATA CREATOR", new ActivityReferenceClassMapping(ReferenceClassName.DATABASE_READER), new ActivityName[]{DATA_REPROCESSOR, DATA_PROCESSOR, DATA_POST_PROCESSOR, DATA_EXPORTER}),
    DATA_QUALITY_ANALYZER("QUALITY ANALYSIS", new ActivityReferenceClassMapping(ReferenceClassName.DATABASE_READER), null);

    private String activityName;
    private ActivityReferenceClassMapping applicableOtherActivity;
    private ActivityName[] subActivity;

    ActivityName(String activityName, ActivityReferenceClassMapping applicableOtherActivity, ActivityName[] subActivity) {
        this.activityName = activityName;
        this.applicableOtherActivity = applicableOtherActivity;
        this.subActivity = subActivity;
    }

    public ActivityReferenceClassMapping getApplicableReferenceClass() {
        return applicableOtherActivity;
    }

    public ActivityName[] getApplicableOtherActivity() {
        return subActivity;
    }

    public String getActivityName() {
        return activityName;
    }
}
