package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.live.watch.dto.SdkPayload;
import com.zhibo.live.watch.dto.WatchSdkResponse;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.WatchSdkBundle;
import com.zhibo.liveprovider.dto.WatchSdkCommand;
import com.zhibo.vhall.provider.VhallLiveProvider;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 观看 SDK 签名（D92/D95/D96）。{@code viewerId} 由 Controller 从观众 JWT 解析后传入；
 * email 签成 {@code {id}@qkzb.com}；{@code sdk.username} 优先用请求里非空的展示昵称
 * （小程序 URL 已按通则算好），昵称不为此再查 viewer。
 * {@code sdk.head} 取 {@code viewer.avatar_url}，没有则为空串，并参与签名。
 * 页壳字段（title/state/playback*）见 {@link WatchService#summary}，不进本响应。
 */
@Service
@RequiredArgsConstructor
public class WatchSdkService {

    private final WatchService watchService;
    private final LiveProviderRegistry providerRegistry;
    private final ViewerRepository viewerRepository;

    public WatchSdkResponse sdk(String activityId, String nickname, Long viewerId) {
        if (viewerId == null || viewerId <= 0) {
            throw new IllegalArgumentException("请先登录");
        }
        ActivityEntity entity = watchService.requireActivity(activityId);
        watchService.requireReadyForWatch(entity);

        String sdkNickname = resolveSdkNickname(nickname, viewerId);
        String head = resolveSdkHead(viewerId);
        WatchSdkBundle bundle = providerRegistry.require(entity.getProvider())
                .buildWatchSdk(new WatchSdkCommand(
                        entity.getProviderRoomId(), sdkNickname, viewerId, head));

        String resolvedNickname = bundle.getSdk() != null && bundle.getSdk().getUsername() != null
                ? bundle.getSdk().getUsername()
                : sdkNickname;

        WatchSdkResponse response = new WatchSdkResponse();
        response.setId(entity.getId());
        response.setProvider(entity.getProvider());
        response.setNickname(resolvedNickname);
        response.setViewerId(viewerId);
        response.setSdk(toSdkPayload(bundle.getSdk()));
        return response;
    }

    /**
     * 有 viewerId 且客户端传了非空 nickname（小程序展示规则串）→ 直接用；
     * 否则走微吼默认解析（空则「观众」）。昵称不查 viewer 表。
     */
    static String resolveSdkNickname(String nickname, Long viewerId) {
        if (viewerId != null && viewerId > 0) {
            if (nickname != null && !nickname.isBlank()) {
                return VhallLiveProvider.resolveNickname(nickname.trim());
            }
        }
        return VhallLiveProvider.resolveNickname(nickname);
    }

    /** {@code viewer.avatar_url} 去空白；没有观众行或没有头像则为 {@code ""}。 */
    String resolveSdkHead(Long viewerId) {
        ViewerEntity viewer = viewerRepository.findById(viewerId).orElse(null);
        if (viewer == null || viewer.getAvatarUrl() == null) {
            return "";
        }
        return viewer.getAvatarUrl().trim();
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
