package io.mosip.packet.core.config.activity;

import io.mosip.packet.core.constant.activity.ActivityName;
import io.mosip.packet.core.constant.ReferenceClassName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@Setter
@Getter
@NoArgsConstructor
public class Activity {

    public Activity(ActivityName activityName) {
        this.activityName = activityName;
    }

    private static Map<String, Activity> activityList = new HashMap<>();
    private boolean isMonitorRequired;
    private ActivityName activityName;
    private List<ReferenceClassName> applicableReferenceClass;
    private List<ActivityName> applicableActivity;

    @Autowired
    ActivityConfig config;

    @Autowired
    ConfigurableEnvironment env;

    @PostConstruct
    public void loadConfiguration() throws Exception {
        for(ActivityName name : ActivityName.values()) {
            Activity activity = new Activity(name);
            String activityName = name.toString().toLowerCase();
            activity.setApplicableReferenceClass(getApplicableReferenceClassList(name));
            activity.setApplicableActivity(getApplicableActivityList(name));

            if(config.getActivity().containsKey(activityName)) {
                Map<String, String> values = config.getActivity().get(activityName);

                for(Map.Entry<String, String> entry : values.entrySet()) {
                    switch(entry.getKey()) {
                        case "monitor":
                            activity.isMonitorRequired = Boolean.valueOf(entry.getValue());
                            break;
                        case "referenceClassEnum":
                            List<ReferenceClassName> list = new ArrayList<>();
                            for(String referenceEnum : entry.getValue().split(",")) {
                                ReferenceClassName refrenceClass =  ReferenceClassName.valueOf(referenceEnum);

                                if(refrenceClass==null)
                                    throw new Exception("Customized Reference Class not Found in ReferenceClassName Enum for " + referenceEnum);

                                list.add(refrenceClass);
                            }
                            activity.setApplicableReferenceClass(list);
                            break;
                        default :
                            activity.isMonitorRequired = false;
                            break;
                    }
                }
            }
            activityList.put(name.name(), activity);
        }
    }

    private List<ReferenceClassName> getApplicableReferenceClassList(ActivityName name) {
        List<ReferenceClassName> list = new ArrayList<>();

        if(name.getApplicableReferenceClass() != null)
            list.addAll(name.getApplicableReferenceClass().getClassList());

        if(name.getApplicableOtherActivity() != null)
            for(ActivityName activityName : name.getApplicableOtherActivity()) {
                list.addAll(getApplicableReferenceClassList(activityName));
            }

        return list;
    }

    private List<ActivityName> getApplicableActivityList(ActivityName name) {
        List<ActivityName> list = new ArrayList<>();
        list.add(name);

        if(name.getApplicableOtherActivity() != null)
            for(ActivityName activityName : name.getApplicableOtherActivity()) {
                list.addAll(getApplicableActivityList(activityName));
            }

        return list;
    }


    public Activity getActivity(String key) throws Exception {
        Activity activity = null;
        if(key == null)
            activity = activityList.get(config.getActivityName());
        else
            activity = activityList.get(key);

        if(activity ==null)
            throw new Exception("Entered Activity Name" + (key != null ? key : config.getActivityName()) + " is Invalid");
        else {
            MutablePropertySources propertySources = env.getPropertySources();
            if(propertySources.contains("newmap"))
                propertySources.remove("newmap");

            Map<String, Object> map = new HashMap<>();
            for(ReferenceClassName referenceClass : activity.applicableReferenceClass) {
                map.put(referenceClass.getProcess().getProperty(), referenceClass.getClassName());
            }

            propertySources.addFirst(new MapPropertySource("newmap", map));
            return activity;
        }
    }
}
