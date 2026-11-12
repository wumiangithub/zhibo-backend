package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.common.SdkAvatarHeads;
import com.zhibo.live.viewer.ViewerAuthException;
import com.zhibo.live.watch.dto.SdkPayload;
import com.zhibo.live.watch.dto.WatchSdkResponse;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.WatchSdkBundle;
import com.zhibo.liveprovider.dto.WatchSdkCommand;
import com.zhibo.vhall.provider.VhallLiveProvider;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 观看 SDK 签名（D92/D95/D96/D111/D118）。{@code viewerUsername} 由 Controller 从观众 JWT 解析后传入；
 * email 签成 {@code {username}@qkzb.com}。
 */
@Service
@RequiredArgsConstructor
public class WatchSdkService {

    private final WatchService watchService;
    private final LiveProviderRegistry providerRegistry;
    private final ViewerRepository viewerRepository;

    @Value("${zhibo.default-avatar-url:" + SdkAvatarHeads.PLACEHOLDER_BASE_URL + "}")
    private String defaultAvatarUrl = SdkAvatarHeads.PLACEHOLDER_BASE_URL;

    public WatchSdkResponse sdk(String activityId, String nickname, String viewerUsername) {
        if (viewerUsername == null || viewerUsername.isBlank()) {
            throw new ViewerAuthException(401, "未登录");
        }
        String username = viewerUsername.trim();
        ActivityEntity entity = watchService.requireActivity(activityId);
        watchService.requireReadyForWatch(entity);

        String sdkNickname = resolveSdkNickname(nickname, username);
        String head = resolveSdkHead(username);
        WatchSdkBundle bundle = providerRegistry.require(entity.getProvider())
                .buildWatchSdk(new WatchSdkCommand(entity.getProviderRoomId(), sdkNickname, username, head));

        String resolvedNickname = bundle.getSdk() != null && bundle.getSdk().getUsername() != null
                ? bundle.getSdk().getUsername()
                : sdkNickname;

        WatchSdkResponse response = new WatchSdkResponse();
        response.setId(entity.getId());
        response.setProvider(entity.getProvider());
        response.setNickname(resolvedNickname);
        response.setUsername(username);
        response.setSdk(toSdkPayload(bundle.getSdk()));
        return response;
    }

    static String resolveSdkNickname(String nickname, String viewerUsername) {
        if (viewerUsername != null && !viewerUsername.isBlank()) {
            if (nickname != null && !nickname.isBlank()) {
                return VhallLiveProvider.resolveNickname(nickname.trim());
            }
        }
        return VhallLiveProvider.resolveNickname(nickname);
    }

    String resolveSdkHead(String viewerUsername) {
        ViewerEntity viewer = viewerRepository.findByUsername(viewerUsername).orElse(null);
        String avatar = viewer == null ? null : viewer.getAvatarUrl();
        return SdkAvatarHeads.resolveForViewer(avatar, viewerUsername, defaultAvatarUrl);
    }

    private static SdkPayload toSdkPayload(WatchSdkBundle.SdkInit sdk) {
        if (sdk == null) {
            return null;
        }
        SdkPayload payload = new SdkPayload();
        payload.setAccount(sdk.getAccount());
        payload.setEmail(sdk.getEmail());
        payload.setUsername(sdk.getUsername());
        payload.setHead(sdk.getHead() == null ? "" : sdk.getHead());
        payload.setWebinarId(sdk.getWebinarId());
        payload.setAppKey(sdk.getAppKey());
        payload.setSignedAt(sdk.getSignedAt());
        if (sdk.getSignType() != null) {
            payload.setSignType(sdk.getSignType());
        }
        payload.setSign(sdk.getSign());
        return payload;
    }
}
