package com.zhibo.live.viewer;

import com.zhibo.live.viewer.dto.ViewerProfileUpdateRequest;
import com.zhibo.live.viewer.dto.ViewerUserResponse;
import com.zhibo.live.volc.ViewerTosUploader;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerStatus;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 观众昵称 / 头像。 */
@Service
public class ViewerProfileService {

    static final int NICK_MAX = 16;
    private static final long AVATAR_MAX_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp");
    private static final String DISABLED = "账号已停用";

    private final ViewerRepository viewerRepository;
    private final ViewerTosUploader tosUploader;
    private final Clock clock;

    public ViewerProfileService(
            ViewerRepository viewerRepository, ViewerTosUploader tosUploader, Clock clock) {
        this.viewerRepository = viewerRepository;
        this.tosUploader = tosUploader;
        this.clock = clock;
    }

    @Transactional
    public ViewerUserResponse update(long viewerId, ViewerProfileUpdateRequest request) {
        ViewerEntity viewer = requireViewer(viewerId);
        if (request == null) {
            throw new ViewerAuthException(400, "请填写昵称或头像");
        }
        boolean hasNick = request.getNickname() != null;
        boolean hasAvatar = request.getAvatarUrl() != null;
        if (!hasNick && !hasAvatar) {
            throw new ViewerAuthException(400, "请填写昵称或头像");
        }
        if (hasNick) {
            applyNickname(viewer, request.getNickname());
        }
        if (hasAvatar) {
            applyAvatarUrl(viewer, request.getAvatarUrl());
        }
        viewer.setUpdatedTime(LocalDateTime.now(clock));
        viewerRepository.save(viewer);
        return toUser(viewer);
    }

    @Transactional
    public ViewerUserResponse uploadAvatar(long viewerId, MultipartFile file) {
        ViewerEntity viewer = requireViewer(viewerId);
        if (file == null || file.isEmpty()) {
            throw new ViewerAuthException(400, "请选择头像");
        }
        if (file.getSize() > AVATAR_MAX_BYTES) {
            throw new ViewerAuthException(400, "头像不能超过2MB");
        }
        String ext = resolveExt(file);
        String contentType = resolveContentType(file, ext);
        if (contentType == null) {
            throw new ViewerAuthException(400, "仅支持 jpg/png/gif/webp 图片");
        }
        String objectKey = ViewerTosUploader.KEY_PREFIX
                + viewerId
                + "_"
                + System.currentTimeMillis()
                + "."
                + ext;
        try (InputStream in = file.getInputStream()) {
            String url = tosUploader.put(objectKey, in, file.getSize(), contentType);
            viewer.setAvatarUrl(url);
            viewer.setUpdatedTime(LocalDateTime.now(clock));
            viewerRepository.save(viewer);
            return toUser(viewer);
        } catch (IOException ex) {
            throw new ViewerAuthException(500, "头像读取失败");
        }
    }

    private ViewerEntity requireViewer(long viewerId) {
        ViewerEntity viewer = viewerRepository.findById(viewerId).orElse(null);
        if (viewer == null) {
            throw new ViewerAuthException(401, "未登录");
        }
        if (!ViewerStatus.isNormal(viewer.getStatus())) {
            throw new ViewerAuthException(403, DISABLED);
        }
        return viewer;
    }

    private static void applyNickname(ViewerEntity viewer, String raw) {
        String nickname = raw == null ? "" : raw.trim();
        if (nickname.isEmpty()) {
            throw new ViewerAuthException(400, "请输入昵称");
        }
        if (nickname.length() > NICK_MAX) {
            throw new ViewerAuthException(400, "昵称最多16字");
        }
        viewer.setNickname(nickname);
    }

    private void applyAvatarUrl(ViewerEntity viewer, String raw) {
        String url = raw == null ? "" : raw.trim();
        if (url.isEmpty()) {
            throw new ViewerAuthException(400, "头像地址无效");
        }
        if (url.length() > 512) {
            throw new ViewerAuthException(400, "头像地址过长");
        }
        if (!tosUploader.isAllowedAvatarUrl(url)) {
            throw new ViewerAuthException(400, "头像地址无效");
        }
        viewer.setAvatarUrl(url);
    }

    private static String resolveExt(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && dot < name.length() - 1) {
                String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
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

    private static String resolveContentType(MultipartFile file, String ext) {
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

    static ViewerUserResponse toUser(ViewerEntity viewer) {
        ViewerUserResponse user = new ViewerUserResponse();
        user.setId(viewer.getId());
        boolean hasPhone = viewer.getPhone() != null && !viewer.getPhone().isBlank();
        user.setHasPhone(hasPhone);
        if (hasPhone) {
            user.setPhoneMasked(ViewerPhoneMask.mask(viewer.getPhone()));
        }
        if (viewer.getNickname() != null && !viewer.getNickname().isBlank()) {
            user.setNickname(viewer.getNickname().trim());
        }
        if (viewer.getAvatarUrl() != null && !viewer.getAvatarUrl().isBlank()) {
            user.setAvatarUrl(viewer.getAvatarUrl().trim());
        }
        return user;
    }
}
