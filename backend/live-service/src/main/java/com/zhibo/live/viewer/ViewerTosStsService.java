package com.zhibo.live.viewer;

import com.volcengine.model.response.AssumeRoleResponse;
import com.zhibo.live.viewer.dto.ViewerTosStsResponse;
import com.zhibo.live.volc.ViewerTosUploader;
import com.zhibo.live.volc.ViewerTosException;
import com.zhibo.live.volc.ViewerVolcStsClient;
import com.zhibo.live.volc.VolcTosProperties;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 观众直传 TOS 的临时凭证。 */
@Service
public class ViewerTosStsService {

    private static final int MIN_DURATION = 900;
    private static final int MAX_DURATION = 3600;

    private final VolcTosProperties properties;
    private final ViewerVolcStsClient stsClient;

    public ViewerTosStsService(VolcTosProperties properties, ViewerVolcStsClient stsClient) {
        this.properties = properties;
        this.stsClient = stsClient;
    }

    public ViewerTosStsResponse issueCredentials() {
        if (properties.stsCredentialsMissing()) {
            throw new IllegalArgumentException("未配置火山 TOS 凭证");
        }

        int duration = clampDuration(properties.getStsDurationSeconds());
        String sessionName = "zhibo-viewer-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        AssumeRoleResponse.Credentials credentials = stsClient.assumeRole(sessionName, duration);

        String accessKeyId = credentials.getAccessKeyId();
        String secretAccessKey = credentials.getSecretAccessKey();
        String sessionToken = credentials.getSessionToken();
        if (isBlank(accessKeyId) || isBlank(secretAccessKey) || isBlank(sessionToken)) {
            throw new ViewerTosException(502, "火山 STS 临时凭证字段不完整");
        }

        String cdn = properties.getTosCdnUrl() == null ? "" : properties.getTosCdnUrl().trim();
        while (cdn.endsWith("/")) {
            cdn = cdn.substring(0, cdn.length() - 1);
        }

        return new ViewerTosStsResponse(
                properties.getTosRegion().trim(),
                stripScheme(properties.getTosEndpoint().trim()),
                properties.getTosBucket().trim(),
                accessKeyId,
                secretAccessKey,
                sessionToken,
                toUtcIso(credentials.getExpiredTime(), duration),
                ViewerTosUploader.KEY_PREFIX,
                cdn);
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

    private static String stripScheme(String endpoint) {
        if (endpoint.startsWith("https://")) {
            return endpoint.substring("https://".length());
        }
        if (endpoint.startsWith("http://")) {
            return endpoint.substring("http://".length());
        }
        return endpoint;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
