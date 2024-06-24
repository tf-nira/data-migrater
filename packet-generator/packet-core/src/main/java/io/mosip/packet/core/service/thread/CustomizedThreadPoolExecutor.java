package io.mosip.packet.core.service.thread;

import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.packet.core.constant.activity.ActivityName;
import io.mosip.packet.core.logger.DataProcessLogger;
import io.mosip.packet.core.util.FixedListQueue;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static io.mosip.packet.core.constant.GlobalConfig.*;

public class CustomizedThreadPoolExecutor {
    List<ThreadPoolExecutor> poolMap = new ArrayList<>();
    private int MAX_THREAD_EXE_COUNT;
    private Long DELAY_SECONDS = 60000L;
    private int maxThreadCount;
    private boolean noSlotAvailable=false;
    private long totalTaskCount = 0;
    private long totalCompletedTaskCount = 0;
    private long failedRecordCount = 0;
    private Long completedCount = 0L;
    private Long currentPendingCount = 0L;
    private int countOfZeroActiveCount = 0;
    private static final Logger LOGGER = DataProcessLogger.getLogger(CustomizedThreadPoolExecutor.class);

    public int getCountOfZeroActiveCount() {
        return countOfZeroActiveCount;
    }

    public long getFailedRecordCount() {
        return failedRecordCount;
    }

    public void increaseFailedRecordCount() {
        failedRecordCount++;
        TOTAL_FAILED_RECORDS++;
    }

    private Timer watch = null;
    private Timer estimateTimer = null;
    private Timer slotAllocationTimer = null;
    private String NAME;
    private FixedListQueue<Long> timeConsumptionPerMin = new FixedListQueue<>(100);
    private FixedListQueue<Integer> countOfProcessPerMin = new FixedListQueue<>(100);
    private boolean isInputProcessCompleted = false;
    private boolean isCompletionCountRequired = false;
    private String trackActivityForCompletion = null;

    public long getTotalCompletedTaskCount() {
        return totalCompletedTaskCount;
    }

    public long getTotalTaskCount() {
        return totalTaskCount;
    }

    public String getNAME() {
        return NAME;
    }

    CountIncrementer failedIncrement = new CountIncrementer() {
        @Override
        public void increment() {
            increaseFailedRecordCount();
        }
    };

    public CustomizedThreadPoolExecutor(Integer threadPoolCount, Integer maxThreadCount, Integer maxThreadExecCount, String poolName) {
        this(threadPoolCount, maxThreadCount, maxThreadExecCount, poolName, true, null);
    }

    public CustomizedThreadPoolExecutor(Integer threadPoolCount, Integer maxThreadCount, Integer maxThreadExecCount, String poolName, Boolean monitorRequired) {
        this(threadPoolCount, maxThreadCount, maxThreadExecCount, poolName, monitorRequired, null);
    }

    public CustomizedThreadPoolExecutor(Integer threadPoolCount, Integer maxThreadCount, Integer maxThreadExecCount, String poolName, Boolean monitorRequired, ActivityName trackActivity) {
        this.NAME = poolName;
        this.maxThreadCount = maxThreadCount;
        this.MAX_THREAD_EXE_COUNT = maxThreadExecCount;

        if(trackActivity != null) {
            this.isCompletionCountRequired = true;
            this.trackActivityForCompletion = trackActivity.getActivityName();
            COMPLETION_COUNT_MAP.put(this.trackActivityForCompletion, Long.valueOf(0L));
        }

        for(int i = 1; i <= threadPoolCount; i++)
            poolMap.add((ThreadPoolExecutor) Executors.newFixedThreadPool(MAX_THREAD_EXE_COUNT));

        slotAllocationTimer = new Timer("Slot Allocation Timer");
        slotAllocationTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                try {
                    if(noSlotAvailable) {
                        boolean isSuccess = false;
                        List<ThreadPoolExecutor> poolMap1 = new ArrayList<>();
                        List<Integer> removeIndex = new ArrayList<>();
                        for(int i=0; i < poolMap.size(); i++) {
                            ThreadPoolExecutor entry = poolMap.get(i);
                            if(entry.getActiveCount() ==0 && entry.getTaskCount() > 0 && entry.getCompletedTaskCount() > 0 && entry.getTaskCount() == entry.getCompletedTaskCount()) {
                                totalTaskCount += entry.getTaskCount();
                                totalCompletedTaskCount += entry.getCompletedTaskCount();
                                removeIndex.add(i);
                                poolMap1.add((ThreadPoolExecutor) Executors.newFixedThreadPool(MAX_THREAD_EXE_COUNT));
                                isSuccess=true;
                            }
                        }

                        Collections.sort(removeIndex, new Comparator<Integer>() {
                            @Override
                            public int compare(Integer o1, Integer o2) {
                                return o2.compareTo(o1);
                            }
                        });

                        for(int i : removeIndex) {
                            ThreadPoolExecutor entry = poolMap.get(i);
                            entry.shutdown();
                            entry.purge();
                            poolMap.remove(i);
                        }

                        if(poolMap1.size() > 0)
                            poolMap.addAll(poolMap1);


                        if(isSuccess)
                            noSlotAvailable=false;
                    }

                    try {
                        Collections.sort(poolMap, new SortbyCount());
                    } catch (ConcurrentModificationException e){}
                } catch (Exception e) {}
            }
        }, 0, 7000L);

        if(monitorRequired) {
            estimateTimer = new Timer("Estimate Time Calculator");
            estimateTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    try {
                        if (TIMECONSUPTIONQUEUE != null && TIMECONSUPTIONQUEUE.size() > 0) {
                            FixedListQueue<Long> listQueue = (FixedListQueue<Long>) TIMECONSUPTIONQUEUE.clone();
                            TIMECONSUPTIONQUEUE.clear();

                            Long avgTime = 0l;
                            Long[] consumedTimeList = listQueue.toArray(new Long[listQueue.size()]);

                            Long TotalSum = Arrays.stream(consumedTimeList).mapToLong(Long::longValue).sum();
                            int noOfRecords = consumedTimeList.length;
                            if(noOfRecords > 0)
                                avgTime = TotalSum / noOfRecords;

                            timeConsumptionPerMin.add(avgTime);
                            countOfProcessPerMin.add(noOfRecords);

                        }
                    } catch (Exception e){}

                }
            }, 0, DELAY_SECONDS);
        }

        watch = new Timer("ThreadPool_Wathcer");
        watch.schedule(new TimerTask() {
            @Override
            public void run() {
                Long totalCount = 0L;
                Long activeCount = 0L;
                completedCount = 0L;
                int totalDays = 0;
                int totalHours = 0;
                int remainingMinutes =0;
                Long avgTime = 0l;
                int avgCount = 0;


                try {
                    for(ThreadPoolExecutor entry : poolMap) {
                        totalCount += entry.getTaskCount();
                        activeCount+= entry.getActiveCount();
                        completedCount+= entry.getCompletedTaskCount();
                    }

                    if(activeCount <= 0)
                        countOfZeroActiveCount++;
                    else
                        countOfZeroActiveCount=0;

                    completedCount+= totalCompletedTaskCount;

                    if((totalTaskCount > 0 || totalCount > 0) && monitorRequired) {
                        // Calculating Estimated Time of Process Completion
                        if(timeConsumptionPerMin != null && timeConsumptionPerMin.size() > 0) {
                            FixedListQueue<Long> listQueue = (FixedListQueue<Long>) timeConsumptionPerMin.clone();
                            FixedListQueue<Integer> countQueue = (FixedListQueue<Integer>)countOfProcessPerMin.clone();

                            Long[] consumedTimeList = listQueue.toArray(new Long[listQueue.size()]);
                            Long totalRecords = TOTAL_RECORDS_FOR_PROCESS;
                            Long TotalSum = Arrays.stream(consumedTimeList).mapToLong(Long::longValue).sum();
                            int noOfRecords = consumedTimeList.length;

                            Integer[] consumedCountList = countQueue.toArray(new Integer[countQueue.size()]);
                            Integer TotalCountSum = Arrays.stream(consumedCountList).mapToInt(Integer::intValue).sum();
                            int noOfCountRecords = consumedCountList.length;
                            avgCount = TotalCountSum/noOfCountRecords;

                            Long remainingRecords = totalRecords - (completedCount + failedRecordCount);
                            avgTime = TotalSum / noOfRecords;
                            Long totalTimeRequired = (remainingRecords / avgCount);

                            totalHours = (int) (totalTimeRequired / 60);
                            totalDays = (int) totalHours / 24;
                            totalHours = (int) (totalHours % 24);
                            remainingMinutes = (int) (totalTimeRequired % 60);
                        }

                        System.out.println("Pool Name : " + NAME + " Avg Count per Min.: " + avgCount + " Avg Time per Record : " + TimeUnit.SECONDS.convert(avgTime, TimeUnit.NANOSECONDS) + "S  Estimate Time of Completion : " + totalDays + "D " + totalHours + "H " + remainingMinutes + "M" +"  Total Records for Process : " + TOTAL_RECORDS_FOR_PROCESS + " Failed in Previous Batch : " + TOTAL_FAILED_RECORDS + "  Total Task : " + (totalTaskCount +totalCount)  + ", Active Task : " + activeCount + ", Completed Task : " + completedCount + ", Failed Task : " + failedRecordCount + (isCompletionCountRequired ? ", No of "+ trackActivityForCompletion + " Completed : " +  COMPLETION_COUNT_MAP.get(trackActivityForCompletion) : ""));
                        LOGGER.info("Pool Name : " + NAME + " Avg Count per Min.: " + avgCount + " Avg Time per Record : " + TimeUnit.SECONDS.convert(avgTime, TimeUnit.NANOSECONDS) + "S  Estimate Time of Completion : " + totalDays + "D " + totalHours + "H " + remainingMinutes + "M" +"  Total Records for Process : " + TOTAL_RECORDS_FOR_PROCESS + " Failed in Previous Batch : " + TOTAL_FAILED_RECORDS + "  Total Task : " + (totalTaskCount +totalCount)  + ", Active Task : " + activeCount + ", Completed Task : " + completedCount + ", Failed Task : " + failedRecordCount + (isCompletionCountRequired ? ", No of "+ trackActivityForCompletion + " Completed : " + COMPLETION_COUNT_MAP.get(trackActivityForCompletion) : ""));
                    }    } catch (Exception e) {}
            }
        }, 0, 90000L);

        THREAD_POOL_EXECUTOR_LIST.add(this);
    }

    public synchronized void ExecuteTask(BaseThreadController task) throws InterruptedException {
        boolean taskAdded = false;
        task.setPoolName(NAME);
        task.setFailedRecordCount(failedIncrement);

        do {
            if(!noSlotAvailable) {
                for(ThreadPoolExecutor entry : poolMap) {
                    if(entry.getTaskCount() <= maxThreadCount) {
                        task.setResponse(new BaseThreadController.SuuccessResponse() {
                            @Override
                            public void onSuccess() {
                                currentPendingCount--;

                                if(COMPLETION_COUNT_MAP.containsKey(NAME)) {
                                    Long count = COMPLETION_COUNT_MAP.get(NAME);
                                    count++;
                                    COMPLETION_COUNT_MAP.put(NAME, count);
                                }
                            }

                            @Override
                            public void onFailure() {
                                currentPendingCount--;
                            }
                        });
                        entry.execute(task);
                        taskAdded=true;
                        currentPendingCount++;
                        break;
                    }
                }

                boolean slotAvailable = false;
                for(ThreadPoolExecutor entry : poolMap) {

                    if(entry.getTaskCount() < maxThreadCount) {
                        slotAvailable = true;
                    }
                }

                if(!slotAvailable) {
                    noSlotAvailable = true;
                }
            }  else {
                TimeUnit.SECONDS.sleep(10);
            }
        } while ((noSlotAvailable && !taskAdded) || !taskAdded);

    }

    class SortbyCount implements Comparator<ThreadPoolExecutor> {
        public int compare(ThreadPoolExecutor a, ThreadPoolExecutor b)
        {
            return Long.valueOf(a.getTaskCount()).compareTo(Long.valueOf(b.getTaskCount()));
        }
    }

    public boolean isBatchAcceptRequest() {
        return !noSlotAvailable;
    }

    public List<ThreadPoolExecutor> getPoolMap() {
        return poolMap;
    }

    public void setInputProcessCompleted(Boolean isCompleted) {
        this.isInputProcessCompleted = isCompleted;
    }

    public boolean getInputProcessCompleted() {
        return this.isInputProcessCompleted;
    }

    public Timer getSlotAllocationTimer() {
        return slotAllocationTimer;
    }

    public void setSlotAllocationTimer(Timer slotAllocationTimer) {
        this.slotAllocationTimer = slotAllocationTimer;
    }

    public Timer getWatch() {
        return watch;
    }

    public void setWatch(Timer watch) {
        this.watch = watch;
    }

    public Timer getEstimateTimer() {
        return estimateTimer;
    }

    public void setEstimateTimer(Timer estimateTimer) {
        this.estimateTimer = estimateTimer;
    }

    public Long getCurrentCompletedTask() {
        return completedCount;
    }

    public Long getCurrentPendingCount() {
        return currentPendingCount;
    }
}
