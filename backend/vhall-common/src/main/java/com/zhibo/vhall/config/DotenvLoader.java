package com.zhibo.vhall.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 读取仓库根目录环境文件（KEY=VALUE），类比前端 Vite。
 * <p>
 * 加载顺序：先 {@code .env}（可进 git），再 {@code .env.local}（本机覆盖，不进 git）。
 * 从 {@code user.dir} 向上找，兼容在 {@code backend/} 或模块目录下启动。
 */
final class DotenvLoader {

    private static final int MAX_PARENT_LEVELS = 6;

    private DotenvLoader() {
    }

    /**
     * 定位含 {@code .env} 的仓库根目录；找不到则返回 null。
     */
    static Path findEnvDir() {
        Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int i = 0; i <= MAX_PARENT_LEVELS; i++) {
            Path candidate = dir.resolve(".env");
            if (Files.isRegularFile(candidate)) {
                return dir;
            }
            Path parent = dir.getParent();
            if (parent == null) {
                break;
            }
            dir = parent;
        }
        return null;
    }

    /**
     * 合并 {@code .env} + 可选 {@code .env.local}（后者覆盖同名键）。
     */
    static Map<String, Object> loadMerged(Path envDir) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        Path env = envDir.resolve(".env");
        if (Files.isRegularFile(env)) {
            map.putAll(load(env));
        }
        Path local = envDir.resolve(".env.local");
        if (Files.isRegularFile(local)) {
            map.putAll(load(local));
        }
        return Collections.unmodifiableMap(map);
    }

    static Map<String, Object> load(Path envFile) throws IOException {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String raw : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int eq = line.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String value = stripQuotes(line.substring(eq + 1).trim());
            if (!key.isEmpty()) {
                map.put(key, value);
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
