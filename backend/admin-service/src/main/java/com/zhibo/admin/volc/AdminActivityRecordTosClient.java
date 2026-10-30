package com.zhibo.admin.volc;

import com.volcengine.tos.TOSV2;
import com.volcengine.tos.TOSV2ClientBuilder;
import com.volcengine.tos.TosException;
import com.volcengine.tos.model.object.ObjectMetaRequestOptions;
import com.volcengine.tos.model.object.PutObjectInput;
import com.zhibo.activity.record.ActivityRecordTosClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 管理端回放成片 PutObject（D88 列表补偿与后续下载重试）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminActivityRecordTosClient implements ActivityRecordTosClient {

    private final VolcProperties properties;

    @Override
    public String putFile(String objectKey, Path file, String contentType) throws IOException {
        if (putCredentialsMissing()) {
            throw new IllegalArgumentException("未配置火山 TOS 凭证");
        }
        if (objectKey == null || objectKey.contains("..") || !objectKey.startsWith(RECORD_PREFIX)) {
            throw new IllegalArgumentException("对象 key 非法");
        }
        long size = Files.size(file);
        String endpoint = stripScheme(properties.getTosEndpoint().trim());
        TOSV2 tos = new TOSV2ClientBuilder()
                .build(
                        properties.getTosRegion().trim(),
                        endpoint,
                        properties.getAccessKey().trim(),
                        properties.getSecretKey().trim());
        try (InputStream in = Files.newInputStream(file)) {
            PutObjectInput input = new PutObjectInput()
                    .setBucket(properties.getTosBucket().trim())
                    .setKey(objectKey)
                    .setContent(in)
                    .setContentLength(size);
            if (contentType != null && !contentType.isBlank()) {
                input.setOptions(new ObjectMetaRequestOptions().setContentType(contentType.trim()));
            }
            tos.putObject(input);
        } catch (TosException ex) {
            log.warn("管理端 TOS putObject 失败 status={} code={} msg={}",
                    ex.getStatusCode(), ex.getCode(), ex.getMessage());
            throw new IOException("回放上传 TOS 失败: " + ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            log.warn("管理端 TOS putObject 异常: {}", ex.getMessage());
            throw new IOException("回放上传 TOS 失败: " + ex.getMessage(), ex);
        }
        return publicUrl(objectKey);
    }

    private String publicUrl(String objectKey) {
        String cdn = properties.getTosCdnUrl() == null ? "" : properties.getTosCdnUrl().trim();
        if (!cdn.isEmpty()) {
            while (cdn.endsWith("/")) {
                cdn = cdn.substring(0, cdn.length() - 1);
            }
            return cdn + "/" + objectKey;
        }
        String endpoint = properties.getTosEndpoint().trim();
        if (endpoint.startsWith("https://") || endpoint.startsWith("http://")) {
            String base = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
            return base + "/" + properties.getTosBucket().trim() + "/" + objectKey;
        }
        return "https://" + properties.getTosBucket().trim() + "." + endpoint + "/" + objectKey;
    }

    private boolean putCredentialsMissing() {
        return isBlank(properties.getAccessKey())
                || isBlank(properties.getSecretKey())
                || isBlank(properties.getTosRegion())
                || isBlank(properties.getTosEndpoint())
                || isBlank(properties.getTosBucket());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
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
