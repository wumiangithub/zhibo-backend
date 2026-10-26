package com.zhibo.live.viewer;

import com.zhibo.live.viewer.dto.ViewerProfileUpdateRequest;
import com.zhibo.live.viewer.dto.ViewerUserResponse;
import com.zhibo.live.volc.ViewerTosUploader;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 观众昵称 / 头像。 */
@Service
public class ViewerProfileService {

    static final int NICK_MAX = 16;
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
        boolean hasUrl = request.getAvatarUrl() != null;
        boolean hasKey = request.getAvatarObjectKey() != null;
        if (hasUrl != hasKey) {
            throw new ViewerAuthException(400, "请同时提交头像地址和对象键");
        }
        if (!hasNick && !hasUrl) {
            throw new ViewerAuthException(400, "请填写昵称或头像");
        }
        if (hasNick) {
            applyNickname(viewer, request.getNickname());
        }
        if (hasUrl) {
            applyAvatar(viewer, request.getAvatarUrl(), request.getAvatarObjectKey());
        }
        viewer.setUpdatedTime(LocalDateTime.now(clock));
        viewerRepository.save(viewer);
        return toUser(viewer);
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

    private void applyAvatar(ViewerEntity viewer, String rawUrl, String rawKey) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        String objectKey = rawKey == null ? "" : rawKey.trim();
        if (url.isEmpty() || objectKey.isEmpty() || url.length() > 512 || objectKey.length() > 1024) {
            throw new ViewerAuthException(400, "头像地址无效");
        }
        if (!objectKey.startsWith(ViewerUploadBiz.AVATAR_PREFIX) || objectKey.contains("..")) {
            throw new ViewerAuthException(400, "头像地址无效");
        }
        if (!url.endsWith("/" + objectKey)) {
            throw new ViewerAuthException(400, "头像地址无效");
        }
        if (!tosUploader.isAllowedAvatarUrl(url)) {
            throw new ViewerAuthException(400, "头像地址无效");
        }
        viewer.setAvatarUrl(url);
        viewer.setAvatarObjectKey(objectKey);
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
        if (viewer.getAvatarObjectKey() != null && !viewer.getAvatarObjectKey().isBlank()) {
            user.setAvatarObjectKey(viewer.getAvatarObjectKey().trim());
        }
        return user;
    }
}
