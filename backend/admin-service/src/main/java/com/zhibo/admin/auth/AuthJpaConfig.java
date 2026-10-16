package com.zhibo.admin.auth;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** 扫描 admin 登录 + 组织 CRM 只读实体；不进 live-common，避免影响 live-service。 */
@Configuration
@EntityScan(basePackages = {"com.zhibo.admin.auth", "com.zhibo.admin.org"})
@EnableJpaRepositories(basePackages = {"com.zhibo.admin.auth", "com.zhibo.admin.org"})
public class AuthJpaConfig {
}
