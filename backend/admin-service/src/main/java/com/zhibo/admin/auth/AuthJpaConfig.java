package com.zhibo.admin.auth;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** 仅扫描 admin 登录实体；不进 live-common，避免影响 live-service。 */
@Configuration
@EntityScan(basePackageClasses = SysUserAccountEntity.class)
@EnableJpaRepositories(basePackageClasses = SysUserAccountRepository.class)
public class AuthJpaConfig {
}
