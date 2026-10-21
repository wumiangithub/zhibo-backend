package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.live.watch.dto.SdkPayload;
import com.zhibo.live.watch.dto.WatchSdkResponse;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.WatchSdkBundle;
import com.zhibo.liveprovider.dto.WatchSdkCommand;
import com.zhibo.vhall.provider.VhallLiveProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WatchSdkService {

    private final WatchService watchService;
    private final LiveProviderRegistry providerRegistry;

    public WatchSdkResponse sdk(String activityId, String guestId, String nickname) {
        ActivityEntity entity = watchService.requireActivity(activityId);
        watchService.requireReadyForWatch(entity);
        WatchSdkBundle bundle = providerRegistry.require(entity.getProvider())
                .buildWatchSdk(new WatchSdkCommand(
                        entity.getProviderRoomId(), guestId, nickname));

        String resolvedGuestId = bundle.getSdk() != null && bundle.getSdk().getAccount() != null
                ? bundle.getSdk().getAccount()
                : VhallLiveProvider.resolveGuestId(guestId);
        String resolvedNickname = bundle.getSdk() != null && bundle.getSdk().getUsername() != null
                ? bundle.getSdk().getUsername()
                : VhallLiveProvider.resolveNickname(nickname);

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
        response.setSdk(toSdkPayload(bundle.getSdk()));
        return response;
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
