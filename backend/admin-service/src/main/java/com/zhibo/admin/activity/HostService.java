package com.zhibo.admin.activity;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.admin.activity.dto.ActivityHostResponse;
import com.zhibo.liveprovider.LiveProviderRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HostService {

    private final ActivityService activityService;
    private final LiveProviderRegistry providerRegistry;

    public ActivityHostResponse host(String activityId) {
        ActivityEntity entity = activityService.requireActivity(activityId);
        if (!BroadcastRules.canBroadcast(
                entity.getReady(), entity.getRiskStatus(), entity.getShelfStatus(), entity.getState())) {
            throw new IllegalArgumentException("当前状态不可开播");
        }
        String hostUrl = providerRegistry.require(entity.getProvider())
                .buildHostUrl(entity.getProviderRoomId());

        ActivityHostResponse response = new ActivityHostResponse();
        response.setProvider(entity.getProvider());
        response.setHostUrl(hostUrl);
        return response;
    }
}
