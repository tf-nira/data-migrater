package io.mosip.packet.core.constant.activity;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.ReferenceClassName;
import io.mosip.packet.core.logger.DataProcessLogger;
import java.util.*;

public class ActivityReferenceClassMapping {
    private Map<String, ReferenceClassName> refClassMap = new HashMap<>();
    private static final Logger LOGGER = DataProcessLogger.getLogger(ActivityReferenceClassMapping.class);


    public ActivityReferenceClassMapping(ReferenceClassName... args) {
        for(ReferenceClassName referenceClassName : args) {
            String processName = referenceClassName.getProcess().name();

            if(refClassMap.containsKey(processName)) {
                System.out.println("Existing Reference Class already mapped for Process ('" + processName + ",. Can not add more than one. Reference Class " + referenceClassName.name());
                LOGGER.warn("Existing Reference Class already mapped for Process ('" + processName + ",. Can not add more than one. Reference Class " + referenceClassName.name());
            } else {
                refClassMap.put(processName, referenceClassName);
            }
        }
    }

    public List<ReferenceClassName> getClassList() {
        return new ArrayList<>(refClassMap.values());
    }
}