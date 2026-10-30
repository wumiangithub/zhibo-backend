package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.PlaybackRules;
import com.zhibo.live.watch.dto.SdkPayload;
import com.zhibo.live.watch.dto.WatchSdkResponse;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.WatchSdkBundle;
import com.zhibo.liveprovider.dto.WatchSdkCommand;
import com.zhibo.vhall.provider.VhallLiveProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 观看 SDK。有 {@code viewerId} 时 email 签成 {@code {id}@qkzb.com}；
 * {@code sdk.username} 优先用请求里非空的展示昵称（小程序 URL 已按通则算好），避免 H5 再拉 user-info。
 * 回放相关字段（D89）一并下发，供结束页 / 本站 TOS 回放页使用。
 */
@Service
@RequiredArgsConstructor
public class WatchSdkService {

    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final WatchService watchService;
    private final LiveProviderRegistry providerRegistry;
    private final Clock clock;

    public WatchSdkResponse sdk(String activityId, String guestId, String nickname, Long viewerId) {
        ActivityEntity entity = watchService.requireActivity(activityId);
        watchService.requireReadyForWatch(entity);

        String sdkNickname = resolveSdkNickname(nickname, viewerId);
        WatchSdkBundle bundle = providerRegistry.require(entity.getProvider())
                .buildWatchSdk(new WatchSdkCommand(
                        entity.getProviderRoomId(), guestId, sdkNickname, viewerId));

        String resolvedGuestId = bundle.getSdk() != null && bundle.getSdk().getAccount() != null
                ? bundle.getSdk().getAccount()
                : VhallLiveProvider.resolveGuestId(guestId);
        String resolvedNickname = bundle.getSdk() != null && bundle.getSdk().getUsername() != null
                ? bundle.getSdk().getUsername()
                : sdkNickname;

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), SHANGHAI);
        boolean playbackEnabled = !Boolean.FALSE.equals(entity.getPlaybackEnabled());

        WatchSdkResponse response = new WatchSdkResponse();
        response.setId(entity.getId());
        response.setProvider(entity.getProvider());
        response.setTitle(bundle.getTitle() != null ? bundle.getTitle() : entity.getTitle());
        response.setState(bundle.getState() != null ? bundle.getState() : entity.getState());
        response.setType(entity.getType());
        response.setNickname(resolvedNickname);
        response.setGuestId(resolvedGuestId);
        response.setStartTime(entity.getStartTime() != null ? entity.getStartTime() : bundle.getStartTime());
        response.setEndTime(entity.getEndTime() != null ? entity.getEndTime() : bundle.getEndTime());
        response.setEmbedUrl(bundle.getEmbedUrl());
        response.setPlaybackEnabled(playbackEnabled);
        response.setPlaybackValidityType(
                entity.getPlaybackValidityType() != null ? entity.getPlaybackValidityType() : "forever");
        response.setPlaybackValidityDays(entity.getPlaybackValidityDays());
        response.setPlaybackExpired(PlaybackRules.isExpired(entity, now));
        response.setPlaybackSpeedAllow(!Boolean.FALSE.equals(entity.getPlaybackSpeedAllow()));
        response.setPlaybackFastForwardAllow(!Boolean.FALSE.equals(entity.getPlaybackFastForwardAllow()));
        response.setSdk(toSdkPayload(bundle.getSdk()));
        return response;
    }

    /**
     * 有 viewerId 且客户端传了非空 nickname（小程序展示规则串）→ 直接用；
     * 否则走微吼默认解析（空则「观众」）。本路径不做 viewer 表查询。
     */
    static String resolveSdkNickname(String nickname, Long viewerId) {
        if (viewerId != null && viewerId > 0) {
            if (nickname != null && !nickname.isBlank()) {
                return VhallLiveProvider.resolveNickname(nickname.trim());
            }
        }
        return VhallLiveProvider.resolveNickname(nickname);
    }

    private static SdkPayload toSdkPayload(WatchSdkBundle.SdkInit sdk) {
        if (sdk == null) {
            return null;
        }
        SdkPayload payload = new SdkPayload();
        payload.setScriptUrl(sdk.getScriptUrl());
        payload.setJqueryUrl(sdk.getJqueryUrl());
        payload.setAccount(sdk.getAccount());
        payload.setEmail(sdk.getEmail());
        payload.setUsername(sdk.getUsername());
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
