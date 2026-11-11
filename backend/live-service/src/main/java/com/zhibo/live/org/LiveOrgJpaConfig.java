package com.zhibo.live.org;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 扫描观看端业务员解析用的 CRM 只读实体（{@code sys_useraccount} / {@code hr_uimsyhxx}）。
 * <p>
 * 须单独 {@code @Configuration}：{@code ActivityJpaConfig} 的 {@code @EnableJpaRepositories}
 * 会关掉 Boot 默认仓库扫描；写在 {@code @SpringBootApplication} 上时本包仓库不会注册。
 */
@Configuration
@EntityScan(basePackages = "com.zhibo.live.org")
@EnableJpaRepositories(basePackages = "com.zhibo.live.org")
public class LiveOrgJpaConfig {
}
