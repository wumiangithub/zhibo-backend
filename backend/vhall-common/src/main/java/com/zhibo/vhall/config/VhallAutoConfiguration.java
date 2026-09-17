package com.zhibo.vhall.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.vhall.client.VhallClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 共享库的“即插即用”入口。
 * <p>
 * 类比：装了依赖就自动生效的 Vite 插件，而不是让业务项目改 {@code scanBasePackages}
 * 去扫 {@code com.zhibo}。真实项目换包名时，只要还依赖 {@code vhall-common}，
 * 这份自动配置仍会生效。
 * <p>
 * {@code vhall-common} 里的 Bean 都从这里注册。不要随手写 {@code @Component}，
 * 应用默认不会扫描 {@code com.zhibo.vhall}。
 */
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(VhallProperties.class)
public class VhallAutoConfiguration {

    public VhallAutoConfiguration(VhallProperties properties) {
        if (!properties.credentialsMissing()) {
            log.info("Vhall 凭证已配置, baseUrl={}", properties.getBaseUrl());
            return;
        }
        if (properties.isFailOnMissingCredentials()) {
            throw new IllegalStateException(
                    "未配置 VHALL_APP_KEY / VHALL_APP_SECRET。"
                            + " 请在仓库根目录复制 .env.example 为 .env 并填写，"
                            + "或使用 --spring.profiles.active=local。");
        }
        log.warn("VHALL_APP_KEY / VHALL_APP_SECRET 为空（未读到 .env），调用微吼会失败。本地练习可先忽略。");
    }

    @Bean
    @ConditionalOnMissingBean
    public VhallClient vhallClient(VhallProperties properties, ObjectMapper objectMapper) {
        return new VhallClient(properties, objectMapper);
    }
}
