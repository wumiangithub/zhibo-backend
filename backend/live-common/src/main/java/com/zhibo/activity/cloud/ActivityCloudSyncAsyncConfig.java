package com.zhibo.activity.cloud;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * D120 旁路云补偿专用线程池：读接口 fire-and-forget，不与回放 TOS 入队抢队列。
 */
@Configuration
public class ActivityCloudSyncAsyncConfig {

    public static final String CLOUD_SYNC_EXECUTOR = "cloudSyncExecutor";

    @Bean(name = CLOUD_SYNC_EXECUTOR)
    public Executor cloudSyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(64);
        executor.setThreadNamePrefix("cloud-sync-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
