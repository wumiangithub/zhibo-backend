package com.zhibo.live.viewer;

import com.zhibo.live.viewer.dto.ViewerUploadResult;
import com.zhibo.live.volc.ViewerTosUploader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 观众文件中转。请求侧与直传 PutObject 对齐：调用方拼好 {@code objectKey}，再交文件。
 * 响应仍是 objectKey / url / location。不写业务表。
 */
@Service
public class ViewerFileUploadService {

    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final int OBJECT_KEY_MAX = 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");

    private final ViewerTosUploader tosUploader;

    public ViewerFileUploadService(ViewerTosUploader tosUploader) {
        this.tosUploader = tosUploader;
    }

    public ViewerUploadResult upload(int biz, String objectKey, MultipartFile file, String contentTypeHint) {
        String prefix = ViewerUploadBiz.prefix(biz);
        String key = objectKey == null ? "" : objectKey.trim();
        if (key.isEmpty() || key.length() > OBJECT_KEY_MAX || key.contains("..") || !key.startsWith(prefix)) {
            throw new ViewerAuthException(400, "对象键无效");
        }
        if (file == null || file.isEmpty()) {
            throw new ViewerAuthException(400, "请选择图片");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new ViewerAuthException(400, "图片不能超过2MB");
        }
        String ext = resolveExt(key, file);
        String contentType = resolveContentType(file, ext, contentTypeHint);
        if (contentType == null) {
            throw new ViewerAuthException(400, "仅支持 jpg/png/gif/webp 图片");
        }
        try (InputStream in = file.getInputStream()) {
            String url = tosUploader.put(key, in, file.getSize(), contentType);
            return new ViewerUploadResult(key, url, tosUploader.location(key));
        } catch (IOException ex) {
            throw new ViewerAuthException(500, "图片读取失败");
        }
    }

    private static String resolveExt(String objectKey, MultipartFile file) {
        int slash = objectKey.lastIndexOf('/');
        String name = slash >= 0 ? objectKey.substring(slash + 1) : objectKey;
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && dot < name.length() - 1) {
            String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
            if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png")
                    || ext.equals("gif") || ext.equals("webp")) {
                return ext.equals("jpeg") ? "jpg" : ext;
            }
        }
        String original = file.getOriginalFilename();
        if (original != null) {
            int oDot = original.lastIndexOf('.');
            if (oDot >= 0 && oDot < original.length() - 1) {
                String ext = original.substring(oDot + 1).toLowerCase(Locale.ROOT);
                if (ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png")
                        || ext.equals("gif") || ext.equals("webp")) {
                    return ext.equals("jpeg") ? "jpg" : ext;
                }
            }
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        return switch (type) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            default -> null;
        };
    }

    private static String resolveContentType(MultipartFile file, String ext, String hint) {
        if (hint != null && !hint.isBlank()) {
            String type = hint.trim().toLowerCase(Locale.ROOT);
            if (ALLOWED_TYPES.contains(type)) {
                return type;
            }
        }
        if (ext == null) {
            return null;
        }
        String type = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (ALLOWED_TYPES.contains(type)) {
            return type;
        }
        return switch (ext) {
            case "jpg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> null;
        };
    }
}
