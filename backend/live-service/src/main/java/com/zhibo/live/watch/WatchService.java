package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityProductEntity;
import com.zhibo.activity.ActivityProductRepository;
import com.zhibo.activity.ActivityReady;
import com.zhibo.activity.ActivityRecordEntity;
import com.zhibo.activity.ActivityRecordRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.PlaybackRules;
import com.zhibo.live.watch.dto.WatchActivityItem;
import com.zhibo.live.watch.dto.WatchActivityListResponse;
import com.zhibo.live.watch.dto.WatchProductItem;
import com.zhibo.live.watch.dto.WatchProductListResponse;
import com.zhibo.live.watch.dto.WatchRecordItem;
import com.zhibo.live.watch.dto.WatchRecordListResponse;
import com.zhibo.live.watch.dto.WatchResponse;
import com.zhibo.live.watch.dto.WatchSummaryResponse;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.ProviderRoomSnapshot;
import com.zhibo.vhall.provider.VhallLiveProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.zhibo.common.UuidIds;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WatchService {

    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private static final DateTimeFormatter CREATED_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ActivityRepository activityRepository;
    private final ActivityProductRepository productRepository;
    private final ActivityRecordRepository activityRecordRepository;
    private final LiveProviderRegistry providerRegistry;
    private final Clock clock;

    public WatchActivityListResponse listActivities(int page, int pageSize, Integer state) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
        boolean statesEmpty = state == null || state == 0;
        List<Integer> statesParam = statesEmpty ? List.of(0) : List.of(state);
        Page<ActivityEntity> result = activityRepository.search(
                null, statesEmpty, statesParam, null, null, null, null, PageRequest.of(page - 1, pageSize));

        WatchActivityListResponse response = new WatchActivityListResponse();
        response.setTotal(result.getTotalElements());
        response.setList(result.getContent().stream()
                .filter(activity -> activity.getDeletedTime() == null)
                .map(this::toItem)
                .toList());
        return response;
    }

    /**
     * 观看页壳摘要（D95）：本库 Activity，无签名、不调微吼。
     */
    public WatchSummaryResponse summary(String activityId) {
        ActivityEntity entity = requireActivity(activityId);
        requireReadyForWatch(entity);

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), SHANGHAI);
        boolean playbackEnabled = !Boolean.FALSE.equals(entity.getPlaybackEnabled());

        WatchSummaryResponse response = new WatchSummaryResponse();
        response.setId(entity.getId());
        response.setProvider(entity.getProvider());
        response.setTitle(entity.getTitle());
        response.setState(entity.getState());
        response.setType(entity.getType());
        response.setStartTime(entity.getStartTime());
        response.setEndTime(entity.getEndTime());
        response.setPlaybackEnabled(playbackEnabled);
        response.setPlaybackValidityType(
                entity.getPlaybackValidityType() != null ? entity.getPlaybackValidityType() : "forever");
        response.setPlaybackValidityDays(entity.getPlaybackValidityDays());
        response.setPlaybackExpired(PlaybackRules.isExpired(entity, now));
        response.setPlaybackSpeedAllow(!Boolean.FALSE.equals(entity.getPlaybackSpeedAllow()));
        response.setPlaybackFastForwardAllow(!Boolean.FALSE.equals(entity.getPlaybackFastForwardAllow()));
        return response;
    }

    public WatchResponse watch(String activityId, String guestId, String nickname, Long viewerId) {
        ActivityEntity entity = requireActivity(activityId);
        requireReadyForWatch(entity);
        LiveProvider provider = providerRegistry.require(entity.getProvider());

        String resolvedGuestId = VhallLiveProvider.resolveGuestId(guestId);
        // 与 /watch/sdk-sign 一致：有 viewerId 时优先用客户端展示昵称（小程序 URL）
        String resolvedNickname = WatchSdkService.resolveSdkNickname(nickname, viewerId);
        String email = VhallLiveProvider.resolveWatchEmail(viewerId, resolvedGuestId);

        ProviderRoomSnapshot info = provider.fetchRoom(entity.getProviderRoomId());
        String baseEmbed = info.fullEmbedShareLink();
        if (baseEmbed == null || baseEmbed.isBlank()) {
            throw new IllegalStateException("活动缺少全屏嵌入地址 full_embed_share_link");
        }
        String embedUrl = VhallLiveProvider.buildEmbedUrlWithEmail(baseEmbed, email, resolvedNickname);

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

    /**
     * 观看端可播回放列表（D89）：本库 {@code status=ready} 且有 {@code tos_url}。
     * 供结束页选片与 {@code <video src>}；不鉴权。
     */
    public WatchRecordListResponse listRecords(String activityId) {
        ActivityEntity entity = requireActivity(activityId);
        requireReadyForWatch(entity);
        WatchRecordListResponse response = new WatchRecordListResponse();
        List<WatchRecordItem> list = activityRecordRepository.findByActivityId(entity.getId()).stream()
                .filter(row -> Integer.valueOf(ActivityRecordEntity.STATUS_READY).equals(row.getStatus()))
                .filter(row -> row.getTosUrl() != null && !row.getTosUrl().isBlank())
                .filter(row -> row.getRecordId() != null && row.getRecordId() > 0)
                .sorted(Comparator
                        .comparing(ActivityRecordEntity::getProviderCreatedTime,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ActivityRecordEntity::getRecordId, Comparator.reverseOrder()))
                .map(this::toWatchRecordItem)
                .toList();
        response.setList(list);
        return response;
    }

    private WatchRecordItem toWatchRecordItem(ActivityRecordEntity row) {
        WatchRecordItem item = new WatchRecordItem();
        item.setId(row.getRecordId());
        item.setTitle(row.getTitle());
        item.setDuration(formatDurationSec(row.getDurationSec()));
        item.setCoverUrl(row.getCoverUrl());
        item.setTosUrl(row.getTosUrl());
        item.setCreatedTime(formatProviderCreatedTime(row.getProviderCreatedTime()));
        return item;
    }

    static String formatDurationSec(Integer durationSec) {
        if (durationSec == null || durationSec < 0) {
            return null;
        }
        int total = durationSec;
        int h = total / 3600;
        int m = (total % 3600) / 60;
        int s = total % 60;
        return String.format("%02d:%02d:%02d", h, m, s);
    }

    private static String formatProviderCreatedTime(LocalDateTime time) {
        if (time == null) {
            return null;
        }
        return time.format(CREATED_TIME);
    }

    /**
     * 观看端小黄车元数据：本场绑定 spu 快照。
     * 订单中心商品由前端直连 {@code viewer/product/list/by-ids}（body.spuIds）。
     */
    public WatchProductListResponse listProducts(String activityId) {
        ActivityEntity entity = requireActivity(activityId);
        requireReadyForWatch(entity);
        List<ActivityProductEntity> rows = productRepository.findByActivityIds(List.of(entity.getId()));
        WatchProductListResponse response = new WatchProductListResponse();
        response.setList(rows.stream().map(this::toProductItem).toList());
        return response;
    }

    private WatchProductItem toProductItem(ActivityProductEntity row) {
        WatchProductItem item = new WatchProductItem();
        item.setSpuId(row.getId().getSpuId());
        item.setProductName(row.getProductName());
        return item;
    }

    ActivityEntity requireActivity(String id) {
        String uuid = UuidIds.require(id, "活动 id 无效", "活动 id 须为本站 UUID");
        return activityRepository.findById(uuid)
                .filter(activity -> activity.getDeletedTime() == null)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
    }

    /** 录播未就绪 / 转码失败时禁止观看与拉 SDK。 */
    void requireReadyForWatch(ActivityEntity entity) {
        Integer ready = entity.getReady();
        if (ActivityReady.isOk(ready)) {
            return;
        }
        if (ready != null && ready == ActivityReady.FAIL) {
            throw new IllegalArgumentException("视频转码失败，暂无法观看");
        }
        throw new IllegalArgumentException("视频转码中，暂无法观看");
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
