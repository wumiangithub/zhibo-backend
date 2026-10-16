package com.zhibo.activity;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 活动表 JPA、{@code LiveProviderRegistry}、进出名单 / 流水走势扫描。
 * 经 AutoConfiguration.imports 加载，不依赖业务包扫 {@code com.zhibo}。
 */
@AutoConfiguration
@EntityScan({"com.zhibo.activity", "com.zhibo.presence", "com.zhibo.material", "com.zhibo.liveaccount", "com.zhibo.liverole"})
@EnableJpaRepositories({"com.zhibo.activity", "com.zhibo.presence", "com.zhibo.material", "com.zhibo.liveaccount", "com.zhibo.liverole"})
@ComponentScan({"com.zhibo.liveprovider", "com.zhibo.attendance", "com.zhibo.presence"})
public class ActivityJpaConfig {
}
