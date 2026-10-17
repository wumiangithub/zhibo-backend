package com.zhibo.admin.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 可注入的系统时钟，便于单测用固定时钟替换。
 * 业务日历（报备第 7 天等）与 JDBC 一致，钉死 Asia/Shanghai。
 */
@Configuration
public class TimeConfig {

    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock clock() {
        return Clock.system(SHANGHAI);
    }
}
