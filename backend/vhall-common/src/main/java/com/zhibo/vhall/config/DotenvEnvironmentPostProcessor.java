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
 * 启动最早阶段把 {@code .env} / {@code .env.local} 灌进 Spring Environment。
 * <p>
 * 不能只塞 {@code VHALL_APP_KEY} 再指望 yml 的 {@code ${VHALL_APP_KEY:}}：
 * 占位符在加载 application.yml 时就会求值，来晚了会变成空字符串。
 * 所以这里同时写入 {@code vhall.*} / {@code volc.*} 属性。
 * <p>
 * 优先级：系统环境变量 / JVM -D &gt; {@code .env.local} &gt; {@code .env}。
 * {@code .env} 可进 git；{@code .env.local} 仅本机、不进 git。
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    static final String PROPERTY_SOURCE_NAME = "dotenvFile";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            return;
        }
        Path envDir = DotenvLoader.findEnvDir();
        if (envDir == null) {
            System.out.println("[dotenv] .env not found from user.dir=" + System.getProperty("user.dir"));
            return;
        }
        try {
            Map<String, Object> loaded = DotenvLoader.loadMerged(envDir);
            if (loaded.isEmpty()) {
                System.out.println("[dotenv] empty: " + envDir.resolve(".env"));
                return;
            }
            Map<String, Object> values = new LinkedHashMap<>(loaded);
            copyIfPresent(values, "VHALL_APP_KEY", "vhall.app-key");
            copyIfPresent(values, "VHALL_APP_SECRET", "vhall.app-secret");
            copyIfPresent(values, "VHALL_CALLBACK_SECRET", "vhall.callback-secret");
            copyIfPresent(values, "VOLC_ACCESS_KEY", "volc.access-key");
            copyIfPresent(values, "VOLC_SECRET_KEY", "volc.secret-key");
            copyIfPresent(values, "VOLC_STS_ROLE_TRN", "volc.sts-role-trn");
            copyIfPresent(values, "VOLC_TOS_REGION", "volc.tos-region");
            copyIfPresent(values, "VOLC_TOS_ENDPOINT", "volc.tos-endpoint");
            copyIfPresent(values, "VOLC_TOS_BUCKET", "volc.tos-bucket");
            copyIfPresent(values, "VOLC_STS_DURATION_SECONDS", "volc.sts-duration-seconds");
            copyIfPresent(values, "VOLC_TOS_CDN_URL", "volc.tos-cdn-url");
            copyIfPresent(values, "AUTH_PASSWORD_AES_KEY_BASE64", "auth.password-aes-key-base64");
            copyIfPresent(values, "AUTH_JWT_SECRET", "auth.jwt-secret");
            copyIfPresent(values, "AUTH_JWT_EXPIRE_SECONDS", "auth.jwt-expire-seconds");
            copyIfPresent(values, "AUTH_JWT_REFRESH_EXPIRE_SECONDS", "auth.jwt-refresh-expire-seconds");
            copyIfPresent(values, "AUTH_JWT_ISSUER", "auth.jwt-issuer");
            copyIfPresent(values, "AUTH_JWT_AUDIENCE", "auth.jwt-audience");
            copyIfPresent(values, "AUTH_JWT_REFRESH_AUDIENCE", "auth.jwt-refresh-audience");
            copyIfPresent(values, "WECHAT_OPEN_APP_ID", "auth.wechat.open-app-id");
            copyIfPresent(values, "WECHAT_OPEN_APP_SECRET", "auth.wechat.open-app-secret");
            copyIfPresent(values, "WECHAT_OPEN_REDIRECT_URI", "auth.wechat.open-redirect-uri");
            copyIfPresent(values, "AUTH_WECHAT_ALLOW_SELF_BIND", "auth.wechat.allow-self-bind");
            // yml 占位符可能在 dotenv 注入前就落成默认端口；这里按服务名写到 server.port 上盖住它。
            applyServerPort(resolveApplicationName(environment, application), values);
            MapPropertySource source = new MapPropertySource(PROPERTY_SOURCE_NAME, values);
            if (environment.getPropertySources().contains(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                environment.getPropertySources().addAfter(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
            } else {
                environment.getPropertySources().addFirst(source);
            }
            Path local = envDir.resolve(".env.local");
            System.out.println("[dotenv] loaded " + envDir.resolve(".env")
                    + (java.nio.file.Files.isRegularFile(local) ? " + .env.local" : ""));
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("读取 .env 失败: " + envDir, ex);
        }
    }

    /**
     * 优先读已加载的 {@code spring.application.name}。yml 还没进来时，用启动类包名区分两个服务。
     */
    static String resolveApplicationName(ConfigurableEnvironment environment, SpringApplication application) {
        String name = environment.getProperty("spring.application.name");
        if (name != null && !name.isBlank()) {
            return name.trim();
        }
        for (Object source : application.getAllSources()) {
            if (!(source instanceof Class)) {
                continue;
            }
            String typeName = ((Class<?>) source).getName();
            if (typeName.startsWith("com.zhibo.admin.")) {
                return "admin-service";
            }
            if (typeName.startsWith("com.zhibo.live.")) {
                return "live-service";
            }
        }
        return "";
    }

    /**
     * 两服务读同一份 .env。只把本服务的端口写到 {@code server.port}，避免互相占用。
     * 空值保持 yml 默认（admin 8081 / live 8082）。
     */
    static void applyServerPort(String applicationName, Map<String, Object> values) {
        String key;
        if ("admin-service".equals(applicationName)) {
            key = "ADMIN_SERVER_PORT";
        } else if ("live-service".equals(applicationName)) {
            key = "LIVE_SERVER_PORT";
        } else {
            return;
        }
        Object raw = values.get(key);
        if (raw == null || String.valueOf(raw).isBlank()) {
            return;
        }
        String text = String.valueOf(raw).trim();
        int port;
        try {
            port = Integer.parseInt(text);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(key + " 不是合法端口: " + text, ex);
        }
        if (port < 1 || port > 65535) {
            throw new IllegalStateException(key + " 超出 1-65535: " + text);
        }
        values.put("server.port", text);
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
