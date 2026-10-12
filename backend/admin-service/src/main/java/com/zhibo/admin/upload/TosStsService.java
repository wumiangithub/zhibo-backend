package com.zhibo.admin.upload;

import com.volcengine.model.response.AssumeRoleResponse;
import com.zhibo.admin.upload.dto.TosStsResponse;
import com.zhibo.admin.volc.VolcProperties;
import com.zhibo.admin.volc.VolcStsClient;
import com.zhibo.admin.volc.VolcStsException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * 用服务端永久 AK/SK 换火山 TOS 短期上传凭证。
 */
@Service
@RequiredArgsConstructor
public class TosStsService {

    private static final int MIN_DURATION = 900;
    private static final int MAX_DURATION = 3600;

    private final VolcProperties properties;
    private final VolcStsClient volcStsClient;

    public TosStsResponse issueCredentials() {
        if (properties.credentialsMissing()) {
            throw new IllegalArgumentException("未配置火山 TOS 凭证");
        }

        int duration = clampDuration(properties.getStsDurationSeconds());
        String sessionName = "zhibo-tos-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        AssumeRoleResponse.Credentials credentials =
                volcStsClient.assumeRole(sessionName, duration);

        String accessKeyId = credentials.getAccessKeyId();
        String secretAccessKey = credentials.getSecretAccessKey();
        String sessionToken = credentials.getSessionToken();
        if (isBlank(accessKeyId) || isBlank(secretAccessKey) || isBlank(sessionToken)) {
            throw new VolcStsException(502, "火山 STS 临时凭证字段不完整");
        }

        return new TosStsResponse(
                properties.getTosRegion(),
                properties.getTosEndpoint(),
                properties.getTosBucket(),
                accessKeyId,
                secretAccessKey,
                sessionToken,
                toUtcIso(credentials.getExpiredTime(), duration));
    }

    static int clampDuration(int seconds) {
        if (seconds < MIN_DURATION) {
            return MIN_DURATION;
        }
        if (seconds > MAX_DURATION) {
            return MAX_DURATION;
        }
        return seconds;
    }

    /**
     * 火山常见 {@code 2026-10-10T18:00:00+08:00}；统一成 UTC {@code Z}。
     * 解析失败时按 duration 从现在推算，避免前端拿不到过期时间。
     */
    static String toUtcIso(String expiredTime, int durationSeconds) {
        if (expiredTime != null && !expiredTime.isBlank()) {
            try {
                return Instant.parse(expiredTime).toString();
            } catch (DateTimeParseException ignored) {
                // fall through
            }
            try {
                return OffsetDateTime.parse(expiredTime).toInstant().toString();
            } catch (DateTimeParseException ignored) {
                // fall through
            }
            try {
                return OffsetDateTime.parse(expiredTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        .toInstant()
                        .toString();
            } catch (DateTimeParseException ignored) {
                // fall through
            }
        }
        return Instant.now().plus(Duration.ofSeconds(durationSeconds)).toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
