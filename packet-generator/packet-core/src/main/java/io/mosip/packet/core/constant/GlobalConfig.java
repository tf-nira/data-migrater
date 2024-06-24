package io.mosip.packet.core.constant;

import io.mosip.packet.core.config.activity.Activity;
import io.mosip.packet.core.constant.activity.ActivityName;
import io.mosip.packet.core.service.thread.CustomizedThreadPoolExecutor;
import io.mosip.packet.core.util.FixedListQueue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadPoolExecutor;

@Component
public class GlobalConfig {

    public static Boolean IS_ONLY_FOR_QUALITY_CHECK = false;

    public static Boolean WRITE_BIOSDK_RESPONSE = false;

    public static String SESSION_KEY;

    public static Boolean IS_TRACKER_REQUIRED = true;

    public static Boolean IS_NETWORK_AVAILABLE = true;

    public static Boolean IS_RUNNING_AS_BATCH = false;

    public static FixedListQueue<Long> TIMECONSUPTIONQUEUE;

    public static Long TOTAL_RECORDS_FOR_PROCESS = 0l;

    public static Long TOTAL_FAILED_RECORDS = 0l;

 //   public static Long ALREADY_PROCESSED_RECORDS = 0L;

    public static Boolean IS_TPM_AVAILABLE;

    public static Boolean IS_DATABASE_READ_OPERATION = false;

    public static Boolean IS_PACKET_CREATOR_OPERATION = false;

    public static Map<String, Long> COMPLETION_COUNT_MAP = new ConcurrentHashMap<>();

    public static List<CustomizedThreadPoolExecutor> THREAD_POOL_EXECUTOR_LIST = new ArrayList<>();

    private static Activity activity;

    public static String getActivityName() {
        return activity.getActivityName().getActivityName();
    }

    public static List<ActivityName> getApplicableActivityList() {
        return activity.getApplicableActivity();
    }

    public static List<ReferenceClassName> getApplicableReferenceClassList() {
        return activity.getApplicableReferenceClass();
    }

    public static void setActivity(Activity activity) {
        GlobalConfig.activity = activity;

        if(activity.getActivityName().equals(ActivityName.DATA_QUALITY_ANALYZER))
            IS_ONLY_FOR_QUALITY_CHECK = true;
        else
            IS_ONLY_FOR_QUALITY_CHECK = false;
    }

    public static boolean isThreadPoolCompleted() throws InterruptedException {
        return isThreadPoolCompleted(null);
    }

    public static boolean isThreadPoolCompleted(String eventName) throws InterruptedException {
        boolean isCompleted = true;

        for(CustomizedThreadPoolExecutor executor : THREAD_POOL_EXECUTOR_LIST)
            if(!executor.isBatchAcceptRequest())
                isCompleted = false;


        if(!isTaskCompleted(eventName))
            isCompleted = false;

        return isCompleted;
    }

    public static Long getPendingCountForProcess() {
        Long pendingCount = 0L;
        for(CustomizedThreadPoolExecutor executor : THREAD_POOL_EXECUTOR_LIST) {
            if(executor.getNAME().equals("QUALITY ANALYSIS") || executor.getNAME().equals("PACKET CREATOR")) {
                pendingCount += executor.getCurrentPendingCount();
            }
        }
        return pendingCount;
    }

    public static boolean isTaskCompleted(String eventName) throws InterruptedException {
        boolean isCompleted = true;
        Integer pendingTaskCount = 0;
        Boolean isAllProcessCompleted = true;

        for(CustomizedThreadPoolExecutor executor : THREAD_POOL_EXECUTOR_LIST) {
            List<ThreadPoolExecutor> executerList = new ArrayList<>(executor.getPoolMap());
            for(ThreadPoolExecutor entry : executerList) {
                pendingTaskCount +=entry.getActiveCount();
            }

            if(!executor.getInputProcessCompleted())
                isAllProcessCompleted = false;
        }

        if(pendingTaskCount > 0 || !isAllProcessCompleted)
            return false;

        if(!IS_DATABASE_READ_OPERATION && !IS_PACKET_CREATOR_OPERATION)
            for(CustomizedThreadPoolExecutor executor : THREAD_POOL_EXECUTOR_LIST) {
                if(eventName == null || eventName.equals(executor.getNAME())) {
                    for(ThreadPoolExecutor entry : executor.getPoolMap()) {
                        if(entry.getActiveCount() > 0) {
                            isCompleted = false;
                            break;
                        } else {
                            if(executor.getInputProcessCompleted() && (entry.getTaskCount() - entry.getCompletedTaskCount() <= 0) && executor.isBatchAcceptRequest() && executor.getCurrentPendingCount() <= 0) {
                                if(executor.getNAME().equals("QUALITY ANALYSIS")) {
                                    if(TOTAL_RECORDS_FOR_PROCESS - TOTAL_FAILED_RECORDS - executor.getCurrentCompletedTask() <= 0 || executor.getCountOfZeroActiveCount() > 10) {
                                        if(executor.getWatch() != null)
                                            executor.getWatch().cancel();
                                        if(executor.getEstimateTimer() != null)
                                            executor.getEstimateTimer().cancel();
                                        if(executor.getSlotAllocationTimer() != null)
                                            executor.getSlotAllocationTimer().cancel();
                                    } else {
                                        isCompleted = false;
                                    }
                                } else {
                                    if(executor.getWatch() != null)
                                        executor.getWatch().cancel();
                                    if(executor.getEstimateTimer() != null)
                                        executor.getEstimateTimer().cancel();
                                    if(executor.getSlotAllocationTimer() != null)
                                        executor.getSlotAllocationTimer().cancel();
                                }
                            } else {
                                isCompleted = false;
                            }
                        }
                    }
                }
            }

        if(IS_DATABASE_READ_OPERATION || IS_PACKET_CREATOR_OPERATION)
            isCompleted = false;

        return isCompleted;
    }
}
