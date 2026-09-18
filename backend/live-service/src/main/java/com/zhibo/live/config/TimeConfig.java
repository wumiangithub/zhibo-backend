package com.zhibo.live.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * 可注入的系统时钟，便于单测用固定时钟替换（与 admin-service 的 TimeConfig 同构）。
 */
@Configuration
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
