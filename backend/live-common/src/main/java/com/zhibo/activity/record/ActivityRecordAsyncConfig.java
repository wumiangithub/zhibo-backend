package com.zhibo.activity.record;

import java.util.concurrent.Executor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 回放落 TOS 等长任务用独立线程池（admin 列表补偿与 live 事件 6 共用）。
 */
@Configuration
@EnableAsync
public class ActivityRecordAsyncConfig {

    public static final String RECORD_INGEST_EXECUTOR = "recordIngestExecutor";

    @Bean(name = RECORD_INGEST_EXECUTOR)
    public Executor recordIngestExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 成片常为 GB 级；core=max、短队列，避免任务堆在队列里看起来像卡死 downloading
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(8);
        executor.setThreadNamePrefix("record-ingest-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
