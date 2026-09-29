package com.zhibo.admin.upload;

import com.zhibo.admin.upload.dto.UploadImageResponse;
import com.zhibo.vhall.client.VhallClient;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 图片上传：本站不落盘，代理到微吼 {@code /v3/commons/upload/index}。
 * <p>
 * 类比前端选图后调自己的上传接口，再拿回 CDN URL 填表单。
 */
@Service
@RequiredArgsConstructor
public class UploadService {

    static final String PATH_UPLOAD = "/v3/commons/upload/index";
    /** 微吼约定：文件字段名；签名时排除。 */
    static final String VHALL_FILE_FIELD = "resfile";

    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> ALLOWED_EXT = Set.of(
            "bmp", "gif", "jpg", "jpeg", "png", "psd");

    private final VhallClient vhallClient;

    public UploadImageResponse uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("图片不能超过 5MB");
        }
        String filename = resolveFilename(file);
        String ext = extensionOf(filename);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException(
                    "仅支持 bmp/gif/jpg/jpeg/png/psd 格式");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("读取上传文件失败", e);
        }

        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        };

        @SuppressWarnings("unchecked")
        Map<String, Object> data = vhallClient.postMultipartForData(
                PATH_UPLOAD,
                Map.of("type", "image"),
                VHALL_FILE_FIELD,
                resource,
                Map.class);

        String url = data == null ? null : asString(data.get("domain_url"));
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("微吼未返回 domain_url");
        }
        return new UploadImageResponse(url.trim());
    }

    private static String resolveFilename(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return "image.jpg";
        }
        // 去掉路径，只留文件名
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        return slash >= 0 ? name.substring(slash + 1) : name;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }
}
