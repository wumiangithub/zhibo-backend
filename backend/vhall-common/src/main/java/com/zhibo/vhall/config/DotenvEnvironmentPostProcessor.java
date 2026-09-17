package com.zhibo.vhall.config;

import java.nio.file.Path;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * 启动最早阶段把 {@code .env} 灌进 Spring Environment，
 * 这样 {@code application.yml} 里的 {@code ${VHALL_APP_KEY:}} 能直接解析。
 * <p>
 * 优先级：系统环境变量 / JVM -D 仍可覆盖 .env（便于 CI）；没设时用 .env。
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "dotenvFile";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Path envFile = DotenvLoader.findEnvFile();
        if (envFile == null) {
            return;
        }
        try {
            Map<String, Object> values = DotenvLoader.load(envFile);
            if (values.isEmpty()) {
                return;
            }
            // addLast：排在 systemEnvironment 之后，OS 环境变量优先
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, values));
        } catch (Exception ex) {
            throw new IllegalStateException("读取 .env 失败: " + envFile, ex);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
