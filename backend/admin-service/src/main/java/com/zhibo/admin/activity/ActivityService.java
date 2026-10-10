package com.zhibo.admin.activity;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.activity.dto.ActivityDetailResponse;
import com.zhibo.admin.activity.dto.ActivityListResponse;
import com.zhibo.admin.activity.dto.ActivityRecordItem;
import com.zhibo.admin.activity.dto.ActivityRecordListResponse;
import com.zhibo.admin.activity.dto.ActivitySummary;
import com.zhibo.admin.activity.dto.CreateActivityRequest;
import com.zhibo.admin.activity.dto.CreateActivityResponse;
import com.zhibo.admin.activity.dto.DeleteActivityResponse;
import com.zhibo.admin.activity.dto.EndActivityResponse;
import com.zhibo.admin.activity.dto.UpdateActivityRequest;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.CreateRoomCommand;
import com.zhibo.liveprovider.dto.CreateRoomResult;
import com.zhibo.liveprovider.dto.ProviderRecordItem;
import com.zhibo.liveprovider.dto.ProviderRecordList;
import com.zhibo.liveprovider.dto.ProviderRoomSnapshot;
import com.zhibo.liveprovider.dto.UpdateRoomCommand;
import com.zhibo.vhall.provider.VhallLiveProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 活动业务：本站落库 + 经 {@link LiveProvider} 调云侧。
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final DateTimeFormatter CREATED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ActivityRepository activityRepository;
    private final LiveProviderRegistry providerRegistry;

    @Transactional
    public CreateActivityResponse create(CreateActivityRequest request) {
        String provider = normalizeProvider(request.getProvider());
        LiveProvider liveProvider = providerRegistry.require(provider);

        String title = request.getTitle() == null ? "" : request.getTitle().trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        if (title.length() > 64) {
            throw new IllegalArgumentException("标题不能超过64个字");
        }
        requireFullDateTime(request.getStartTime(), "开始时间");
        validateOptionalEndTime(request.getEndTime(), request.getStartTime());

        String introduction = trimToNull(request.getIntroduction());
        if (introduction != null && introduction.length() > 256) {
            throw new IllegalArgumentException("简介不能超过256个字");
        }

        Integer type = request.getType();
        if (type == null || type < 1 || type > 4) {
            throw new IllegalArgumentException("直播类型无效");
        }

        CreateRoomResult room = liveProvider.create(new CreateRoomCommand(
                title,
                request.getStartTime().trim(),
                type,
                introduction,
                request.getRecordId(),
                trimToNull(request.getCoverUrl()),
                request.getUltraLowLatency()
        ));

        ActivityEntity entity = new ActivityEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setProvider(provider);
        entity.setProviderRoomId(room.providerRoomId());
        entity.setTitle(title);
        entity.setIntroduction(introduction);
        entity.setStartTime(request.getStartTime().trim());
        entity.setEndTime(trimToNull(request.getEndTime()));
        entity.setType(type);
        entity.setState(type == 4 ? 4 : 2);
        entity.setViewerStyle(normalizeViewerStyle(request.getViewerStyle()));
        entity.setUltraLowLatency(request.getUltraLowLatency());
        entity.setVirtualAvatar(request.getVirtualAvatar());
        entity.setCoverUrl(trimToNull(request.getCoverUrl()));
        entity.setWarmupType(trimToNull(request.getWarmupType()));
        entity.setWarmupUrl(trimToNull(request.getWarmupUrl()));
        entity.setPromoCoverUrl(trimToNull(request.getPromoCoverUrl()));
        entity.setDetailHtml(trimToNull(request.getDetailHtml()));

        try {
            ProviderRoomSnapshot snap = liveProvider.fetchRoom(room.providerRoomId());
            if (snap.state() != null) {
                entity.setState(snap.state());
            }
            if (snap.shareLink() != null) {
                entity.setShareLink(snap.shareLink());
            }
            if (entity.getCoverUrl() == null && snap.coverUrl() != null) {
                entity.setCoverUrl(snap.coverUrl());
            }
        } catch (RuntimeException ignored) {
            // 创建已成功；拉详情失败不挡落库
        }

        activityRepository.save(entity);
        return new CreateActivityResponse(entity.getId());
    }

    public ActivityListResponse list(int page, int pageSize, String keyword, Integer state, String provider) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
        Integer stateFilter = (state != null && state == 0) ? null : state;
        String providerFilter = (provider == null || provider.isBlank()) ? null : provider.trim();
        String keywordFilter = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        Page<ActivityEntity> result = activityRepository.search(
                providerFilter, stateFilter, keywordFilter, PageRequest.of(page - 1, pageSize));

        ActivityListResponse response = new ActivityListResponse();
        response.setTotal(result.getTotalElements());
        response.setList(result.getContent().stream().map(this::toSummary).toList());
        return response;
    }

    public ActivityDetailResponse detail(String id) {
        ActivityEntity entity = requireActivity(id);
        LiveProvider liveProvider = providerRegistry.require(entity.getProvider());

        ActivityDetailResponse detail = toDetail(entity);
        try {
            ProviderRoomSnapshot snap = liveProvider.fetchRoom(entity.getProviderRoomId());
            mergeCloudFields(detail, entity, snap);
            if (snap.state() != null && !snap.state().equals(entity.getState())) {
                entity.setState(snap.state());
                activityRepository.save(entity);
            }
            if (snap.shareLink() != null) {
                entity.setShareLink(snap.shareLink());
                activityRepository.save(entity);
            }
        } catch (RuntimeException ignored) {
            // 以本站库为准
        }
        return detail;
    }

    @Transactional
    public CreateActivityResponse update(String id, UpdateActivityRequest request) {
        ActivityEntity entity = requireActivity(id);
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }

        boolean hasTitle = request.getTitle() != null && !request.getTitle().isBlank();
        boolean hasStart = request.getStartTime() != null && !request.getStartTime().isBlank();
        boolean hasIntro = request.getIntroduction() != null;
        boolean hasEnd = request.getEndTime() != null;
        boolean localOnly = request.getViewerStyle() != null
                || request.getUltraLowLatency() != null
                || request.getVirtualAvatar() != null
                || request.getCoverUrl() != null
                || request.getWarmupType() != null
                || request.getWarmupUrl() != null
                || request.getPromoCoverUrl() != null
                || request.getDetailHtml() != null;

        if (!hasTitle && !hasStart && !hasIntro && !hasEnd && !localOnly) {
            throw new IllegalArgumentException("至少填写一个可改字段");
        }

        if (hasTitle) {
            String title = request.getTitle().trim();
            if (title.length() > 64) {
                throw new IllegalArgumentException("标题不能超过64个字");
            }
            entity.setTitle(title);
        }
        if (hasStart) {
            requireFullDateTime(request.getStartTime(), "开始时间");
            entity.setStartTime(request.getStartTime().trim());
        }
        if (hasIntro) {
            String intro = request.getIntroduction().trim();
            if (intro.length() > 256) {
                throw new IllegalArgumentException("简介不能超过256个字");
            }
            entity.setIntroduction(intro.isEmpty() ? null : intro);
        }
        if (hasEnd) {
            String end = trimToNull(request.getEndTime());
            String start = entity.getStartTime();
            if (end != null) {
                validateOptionalEndTime(end, start);
            }
            entity.setEndTime(end);
        }
        if (request.getViewerStyle() != null) {
            entity.setViewerStyle(normalizeViewerStyle(request.getViewerStyle()));
        }
        if (request.getUltraLowLatency() != null) {
            entity.setUltraLowLatency(request.getUltraLowLatency());
        }
        if (request.getVirtualAvatar() != null) {
            entity.setVirtualAvatar(request.getVirtualAvatar());
        }
        if (request.getCoverUrl() != null) {
            entity.setCoverUrl(trimToNull(request.getCoverUrl()));
        }
        if (request.getWarmupType() != null) {
            entity.setWarmupType(trimToNull(request.getWarmupType()));
        }
        if (request.getWarmupUrl() != null) {
            entity.setWarmupUrl(trimToNull(request.getWarmupUrl()));
        }
        if (request.getPromoCoverUrl() != null) {
            entity.setPromoCoverUrl(trimToNull(request.getPromoCoverUrl()));
        }
        if (request.getDetailHtml() != null) {
            entity.setDetailHtml(trimToNull(request.getDetailHtml()));
        }

        boolean syncCloud = hasTitle || hasStart || hasIntro || request.getCoverUrl() != null;
        if (syncCloud) {
            LiveProvider liveProvider = providerRegistry.require(entity.getProvider());
            // coverUrl：请求里带了就同步（空串也可，便于清空）；未带则 null=不改微吼
            String coverForCloud = request.getCoverUrl() == null
                    ? null
                    : request.getCoverUrl().trim();
            liveProvider.update(entity.getProviderRoomId(), new UpdateRoomCommand(
                    hasTitle ? entity.getTitle() : null,
                    hasStart ? entity.getStartTime() : null,
                    hasIntro ? entity.getIntroduction() : null,
                    coverForCloud
            ));
        }

        activityRepository.save(entity);
        return new CreateActivityResponse(entity.getId());
    }

    @Transactional
    public EndActivityResponse end(String id) {
        ActivityEntity entity = requireActivity(id);
        LiveProvider liveProvider = providerRegistry.require(entity.getProvider());
        int state = liveProvider.end(entity.getProviderRoomId());
        entity.setState(state);
        activityRepository.save(entity);
        return new EndActivityResponse(entity.getId(), state);
    }

    @Transactional
    public DeleteActivityResponse delete(String id) {
        ActivityEntity entity = requireActivity(id);
        LiveProvider liveProvider = providerRegistry.require(entity.getProvider());
        liveProvider.delete(entity.getProviderRoomId());
        activityRepository.delete(entity);
        return new DeleteActivityResponse(entity.getId());
    }

    /**
     * 某场本站活动下的微吼回放列表；{@code list[].id} 供 create-demand 的 recordId。
     */
    public ActivityRecordListResponse listRecords(String id) {
        ActivityEntity entity = requireActivity(id);
        LiveProvider liveProvider = providerRegistry.require(entity.getProvider());
        ProviderRecordList records = liveProvider.listRecords(entity.getProviderRoomId());
        ActivityRecordListResponse response = new ActivityRecordListResponse();
        response.setTotal(records.total());
        List<ActivityRecordItem> list = new ArrayList<>();
        for (ProviderRecordItem item : records.list()) {
            list.add(new ActivityRecordItem(
                    item.id(),
                    item.title(),
                    item.duration(),
                    item.createdAt(),
                    item.coverUrl()
            ));
        }
        response.setList(list);
        return response;
    }

    public ActivityEntity requireActivity(String id) {
        String uuid = ActivityIds.requireUuid(id);
        return activityRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
    }

    private ActivitySummary toSummary(ActivityEntity entity) {
        ActivitySummary summary = new ActivitySummary();
        summary.setId(entity.getId());
        summary.setProvider(entity.getProvider());
        summary.setTitle(entity.getTitle());
        summary.setStartTime(entity.getStartTime());
        summary.setCoverUrl(entity.getCoverUrl());
        summary.setState(entity.getState());
        summary.setType(entity.getType());
        summary.setViewerStyle(entity.getViewerStyle());
        summary.setShareLink(entity.getShareLink());
        if (entity.getCreatedAt() != null) {
            summary.setCreatedAt(entity.getCreatedAt().format(CREATED_AT));
        }
        return summary;
    }

    private ActivityDetailResponse toDetail(ActivityEntity entity) {
        ActivityDetailResponse detail = new ActivityDetailResponse();
        ActivitySummary base = toSummary(entity);
        detail.setId(base.getId());
        detail.setProvider(base.getProvider());
        detail.setTitle(base.getTitle());
        detail.setStartTime(base.getStartTime());
        detail.setCoverUrl(base.getCoverUrl());
        detail.setState(base.getState());
        detail.setType(base.getType());
        detail.setViewerStyle(base.getViewerStyle());
        detail.setShareLink(base.getShareLink());
        detail.setCreatedAt(base.getCreatedAt());
        detail.setProviderRoomId(entity.getProviderRoomId());
        if (VhallLiveProvider.CODE.equals(entity.getProvider())) {
            detail.setVhallWebinarId(entity.getProviderRoomId());
        }
        detail.setIntroduction(entity.getIntroduction());
        detail.setEndTime(entity.getEndTime());
        detail.setUltraLowLatency(entity.getUltraLowLatency());
        detail.setVirtualAvatar(entity.getVirtualAvatar());
        detail.setWarmupType(entity.getWarmupType());
        detail.setWarmupUrl(entity.getWarmupUrl());
        detail.setPromoCoverUrl(entity.getPromoCoverUrl());
        detail.setDetailHtml(entity.getDetailHtml());
        return detail;
    }

    private void mergeCloudFields(
            ActivityDetailResponse detail, ActivityEntity entity, ProviderRoomSnapshot snap) {
        if (snap.state() != null) {
            detail.setState(snap.state());
        }
        if (snap.pv() != null) {
            detail.setPv(snap.pv());
        }
        if (snap.verify() != null) {
            detail.setVerify(snap.verify());
        }
        if (snap.fullEmbedShareLink() != null) {
            detail.setFullEmbedShareLink(snap.fullEmbedShareLink());
        }
        if (snap.shareLink() != null) {
            detail.setShareLink(snap.shareLink());
        }
        // 本站有 endTime 用本站；否则用云侧有效结束时间
        if (detail.getEndTime() == null && snap.endTime() != null) {
            detail.setEndTime(snap.endTime());
        }
    }

    static void validateOptionalEndTime(String endTime, String startTime) {
        if (endTime == null || endTime.isBlank()) {
            return;
        }
        String end = endTime.trim();
        requireFullDateTime(end, "结束时间");
        String start = startTime == null ? "" : startTime.trim();
        requireFullDateTime(start, "开始时间");
        if (end.compareTo(start) <= 0) {
            throw new IllegalArgumentException("结束时间须晚于开始时间");
        }
    }

    private static void requireFullDateTime(String value, String field) {
        if (value == null || !value.trim().matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}")) {
            throw new IllegalArgumentException(field + "格式须为 yyyy-MM-dd HH:mm:ss");
        }
    }

    private static String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("provider 不能为空");
        }
        return provider.trim().toLowerCase();
    }

    private static String normalizeViewerStyle(String viewerStyle) {
        if (viewerStyle == null || viewerStyle.isBlank()) {
            return "traditional";
        }
        String value = viewerStyle.trim();
        if (!"traditional".equals(value) && !"immersive".equals(value)) {
            throw new IllegalArgumentException("viewerStyle 无效");
        }
        return value;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
