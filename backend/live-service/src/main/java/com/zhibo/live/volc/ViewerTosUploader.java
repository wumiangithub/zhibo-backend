package com.zhibo.live.volc;

import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TOSV2ClientBuilder;
import com.volcengine.tos.TosException;
import com.volcengine.tos.model.object.ObjectMetaRequestOptions;
import com.volcengine.tos.model.object.PutObjectInput;
import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 观众文件上传到火山 TOS。对象 key 必须落在已登记前缀下。
 */
@Slf4j
@Component
public class ViewerTosUploader {

    /** 观众头像。 */
    public static final String KEY_PREFIX = "viewer/avatar/";

    /** 意见反馈截图。 */
    public static final String FEEDBACK_PREFIX = "viewer/feedback/";

    private final VolcTosProperties properties;

    public ViewerTosUploader(VolcTosProperties properties) {
        this.properties = properties;
    }

    /**
     * @param objectKey 含前缀的完整 key，如 {@code viewer/avatar/1_171.jpg}
     * @return 可访问 URL（优先 CDN）
     */
    public String put(String objectKey, InputStream body, long contentLength, String contentType) {
        if (properties.credentialsMissing()) {
            throw new IllegalArgumentException("未配置火山 TOS 凭证");
        }
        if (!isRegisteredKey(objectKey)) {
            throw new IllegalArgumentException("对象 key 非法");
        }
        String endpoint = stripScheme(properties.getTosEndpoint().trim());
        TOSV2 tos = new TOSV2ClientBuilder()
                .build(
                        properties.getTosRegion().trim(),
                        endpoint,
                        properties.getAccessKey().trim(),
                        properties.getSecretKey().trim());
        try {
            PutObjectInput input = new PutObjectInput()
                    .setBucket(properties.getTosBucket().trim())
                    .setKey(objectKey)
                    .setContent(body)
                    .setContentLength(contentLength);
            if (contentType != null && !contentType.isBlank()) {
                input.setOptions(new ObjectMetaRequestOptions().setContentType(contentType.trim()));
            }
            tos.putObject(input);
        } catch (TosException ex) {
            log.warn("TOS putObject 失败 status={} code={} msg={}",
                    ex.getStatusCode(), ex.getCode(), ex.getMessage());
            throw new ViewerTosException(502, "文件上传失败，请重试");
        } catch (RuntimeException ex) {
            log.warn("TOS putObject 异常: {}", ex.getMessage());
            throw new ViewerTosException(502, "文件上传失败，请重试");
        }
        return publicUrl(objectKey);
    }

    /** 桶上的地址，对应直传 SDK 的 Location。 */
    public String location(String objectKey) {
        String endpoint = stripScheme(properties.getTosEndpoint() == null ? "" : properties.getTosEndpoint().trim());
        String bucket = properties.getTosBucket() == null ? "" : properties.getTosBucket().trim();
        return "https://" + bucket + "." + endpoint + "/" + objectKey;
    }

    public String publicUrl(String objectKey) {
        String cdn = properties.getTosCdnUrl() == null ? "" : properties.getTosCdnUrl().trim();
        if (!cdn.isEmpty()) {
            while (cdn.endsWith("/")) {
                cdn = cdn.substring(0, cdn.length() - 1);
            }
            return cdn + "/" + objectKey;
        }
        String endpoint = properties.getTosEndpoint().trim();
        if (endpoint.startsWith("https://") || endpoint.startsWith("http://")) {
            // already absolute host with scheme — rare
            String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
            return base + "/" + properties.getTosBucket().trim() + "/" + objectKey;
        }
        return "https://" + properties.getTosBucket().trim() + "." + endpoint + "/" + objectKey;
    }

    /** 校验客户端回传的 avatarUrl 是否落在本站 CDN / 原生桶。 */
    public boolean isAllowedAvatarUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        String trimmed = url.trim();
        if (!(trimmed.startsWith("https://") || trimmed.startsWith("http://"))) {
            return false;
        }
        String cdn = properties.getTosCdnUrl() == null ? "" : properties.getTosCdnUrl().trim();
        if (!cdn.isEmpty()) {
            while (cdn.endsWith("/")) {
                cdn = cdn.substring(0, cdn.length() - 1);
            }
            if (trimmed.startsWith(cdn + "/") || trimmed.equals(cdn)) {
                return true;
            }
        }
        String endpoint = stripScheme(properties.getTosEndpoint() == null ? "" : properties.getTosEndpoint().trim());
        String bucket = properties.getTosBucket() == null ? "" : properties.getTosBucket().trim();
        if (!bucket.isEmpty() && !endpoint.isEmpty()) {
            String nativeBase = "https://" + bucket + "." + endpoint;
            if (trimmed.startsWith(nativeBase + "/") || trimmed.equals(nativeBase)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRegisteredKey(String objectKey) {
        return objectKey != null
                && !objectKey.contains("..")
                && (objectKey.startsWith(KEY_PREFIX) || objectKey.startsWith(FEEDBACK_PREFIX));
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
}
