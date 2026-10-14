package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.live.watch.dto.WatchActivityItem;
import com.zhibo.live.watch.dto.WatchActivityListResponse;
import com.zhibo.live.watch.dto.WatchResponse;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.ProviderRoomSnapshot;
import com.zhibo.vhall.provider.VhallLiveProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WatchService {

    private final ActivityRepository activityRepository;
    private final LiveProviderRegistry providerRegistry;

    public WatchActivityListResponse listActivities(int page, int pageSize, Integer state) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
        Integer stateFilter = (state != null && state == 0) ? null : state;
        Page<ActivityEntity> result = activityRepository.search(
                null, stateFilter, null, null, null, null, PageRequest.of(page - 1, pageSize));

        WatchActivityListResponse response = new WatchActivityListResponse();
        response.setTotal(result.getTotalElements());
        response.setList(result.getContent().stream()
                .filter(activity -> activity.getDeletedAt() == null)
                .map(this::toItem)
                .toList());
        return response;
    }

    public WatchResponse watch(String activityId, String guestId, String nickname) {
        ActivityEntity entity = requireActivity(activityId);
        LiveProvider provider = providerRegistry.require(entity.getProvider());

        String resolvedGuestId = VhallLiveProvider.resolveGuestId(guestId);
        String resolvedNickname = VhallLiveProvider.resolveNickname(nickname);

        ProviderRoomSnapshot info = provider.fetchRoom(entity.getProviderRoomId());
        String baseEmbed = info.fullEmbedShareLink();
        if (baseEmbed == null || baseEmbed.isBlank()) {
            throw new IllegalStateException("活动缺少全屏嵌入地址 full_embed_share_link");
        }
        String embedUrl = VhallLiveProvider.buildEmbedUrl(baseEmbed, resolvedGuestId, resolvedNickname);

        WatchResponse response = new WatchResponse();
        response.setId(entity.getId());
        response.setProvider(entity.getProvider());
        response.setTitle(entity.getTitle());
        response.setState(info.state() != null ? info.state() : entity.getState());
        response.setType(entity.getType());
        response.setNickname(resolvedNickname);
        response.setGuestId(resolvedGuestId);
        response.setStartTime(entity.getStartTime() != null ? entity.getStartTime() : info.startTime());
        response.setEndTime(entity.getEndTime() != null ? entity.getEndTime() : info.endTime());
        response.setEmbedUrl(embedUrl);
        return response;
    }

    ActivityEntity requireActivity(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("活动 id 无效");
        }
        String uuid;
        try {
            uuid = UUID.fromString(id.trim()).toString();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("活动 id 须为本站 UUID");
        }
        return activityRepository.findById(uuid)
                .filter(activity -> activity.getDeletedAt() == null)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
    }

    private WatchActivityItem toItem(ActivityEntity entity) {
        WatchActivityItem item = new WatchActivityItem();
        item.setId(entity.getId());
        item.setTitle(entity.getTitle());
        item.setState(entity.getState());
        item.setStartTime(entity.getStartTime());
        item.setCoverUrl(entity.getCoverUrl());
        return item;
    }
}
