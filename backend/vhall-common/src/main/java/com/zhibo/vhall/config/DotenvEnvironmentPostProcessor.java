package com.zhibo.vhall.config;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * 启动最早阶段把 {@code .env} 灌进 Spring Environment。
 * <p>
 * 不能只塞 {@code VHALL_APP_KEY} 再指望 yml 的 {@code ${VHALL_APP_KEY:}}：
 * 占位符在加载 application.yml 时就会求值，来晚了会变成空字符串。
 * 所以这里同时写入 {@code vhall.app-key} / {@code vhall.app-secret}，
 * 并把 PropertySource 插到系统环境变量之后、application.yml 之前。
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
            System.out.println("[dotenv] .env not found from user.dir=" + System.getProperty("user.dir"));
            return;
        }
        try {
            Map<String, Object> loaded = DotenvLoader.load(envFile);
            if (loaded.isEmpty()) {
                System.out.println("[dotenv] empty file: " + envFile);
                return;
            }
            Map<String, Object> values = new LinkedHashMap<>(loaded);
            copyIfPresent(values, "VHALL_APP_KEY", "vhall.app-key");
            copyIfPresent(values, "VHALL_APP_SECRET", "vhall.app-secret");
            MapPropertySource source = new MapPropertySource(PROPERTY_SOURCE_NAME, values);
            if (environment.getPropertySources().contains(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                environment.getPropertySources().addAfter(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
            } else {
                environment.getPropertySources().addFirst(source);
            }
            System.out.println("[dotenv] loaded " + envFile);
        } catch (Exception ex) {
            throw new IllegalStateException("读取 .env 失败: " + envFile, ex);
        }
    }

    private static void copyIfPresent(Map<String, Object> values, String from, String to) {
        Object value = values.get(from);
        if (value != null && !String.valueOf(value).isBlank()) {
            values.put(to, value);
        }
    }

    @Override
    public int getOrder() {
        // 等 application.yml 进 Environment 之后再插，才能压在它上面
        return Ordered.LOWEST_PRECEDENCE;
    }
}
