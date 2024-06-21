package io.mosip.packet.core.constant.activity;

import io.mosip.packet.core.constant.ReferenceClassName;

public enum ActivityName {
    DATA_EXPORTER("DATA UPLOADER", null, null),
    DATA_CREATOR("DATA CREATOR", new ActivityReferenceClassMapping(ReferenceClassName.DATABASE_READER, ReferenceClassName.MOSIP_PACKET_UPLOAD), new ActivityName[]{DATA_EXPORTER}),
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
}
