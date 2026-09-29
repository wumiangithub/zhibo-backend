package com.zhibo.admin.activity;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.admin.activity.dto.ActivityEmbedResponse;
import com.zhibo.liveprovider.LiveProviderRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmbedService {

    private final ActivityService activityService;
    private final LiveProviderRegistry providerRegistry;

    public ActivityEmbedResponse embed(String activityId) {
        ActivityEntity entity = activityService.requireActivity(activityId);
        String embedUrl = providerRegistry.require(entity.getProvider())
                .buildAdminEmbedUrl(entity.getProviderRoomId());

        ActivityEmbedResponse response = new ActivityEmbedResponse();
        response.setEmbedUrl(embedUrl);
        return response;
    }
}
