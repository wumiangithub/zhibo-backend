package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.live.watch.dto.ChatFilterResponse;
import com.zhibo.liveprovider.LiveProviderRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatFilterService {

    private final WatchService watchService;
    private final LiveProviderRegistry providerRegistry;

    public ChatFilterResponse chatFilter(String activityId, String email, String name) {
        ActivityEntity entity = watchService.requireActivity(activityId);
        String pageUrl = providerRegistry.require(entity.getProvider())
                .buildChatFilterUrl(entity.getProviderRoomId(), email, name);

        ChatFilterResponse response = new ChatFilterResponse();
        response.setPageUrl(pageUrl);
        return response;
    }
}
