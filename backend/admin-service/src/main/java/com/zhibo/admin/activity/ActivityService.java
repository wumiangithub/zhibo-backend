package com.zhibo.admin.activity;

import com.zhibo.activity.ActivityAuditEntity;
import com.zhibo.activity.ActivityAuditRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityOwnerEntity;
import com.zhibo.activity.ActivityOwnerId;
import com.zhibo.activity.ActivityOwnerRepository;
import com.zhibo.activity.ActivityProductEntity;
import com.zhibo.activity.ActivityProductId;
import com.zhibo.activity.ActivityProductRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.activity.dto.ActivityDetailResponse;
import com.zhibo.admin.activity.dto.ActivityListResponse;
import com.zhibo.admin.activity.dto.ActivityPerson;
import com.zhibo.admin.activity.dto.ActivityProductView;
import com.zhibo.admin.activity.dto.ActivityRecordItem;
import com.zhibo.admin.activity.dto.ActivityRecordListResponse;
import com.zhibo.admin.activity.dto.ActivitySummary;
import com.zhibo.admin.activity.dto.AuditListResponse;
import com.zhibo.admin.activity.dto.AuditRecordItem;
import com.zhibo.admin.activity.dto.AuditRequest;
import com.zhibo.admin.activity.dto.AuditResultResponse;
import com.zhibo.admin.activity.dto.CreateActivityRequest;
import com.zhibo.admin.activity.dto.CreateActivityResponse;
import com.zhibo.admin.activity.dto.DeleteActivityResponse;
import com.zhibo.admin.activity.dto.EndActivityResponse;
import com.zhibo.admin.activity.dto.ShelfRequest;
import com.zhibo.admin.activity.dto.ShelfResponse;
import com.zhibo.admin.activity.dto.UpdateActivityRequest;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 活动业务：本站落库 + 经 {@link LiveProvider} 调云侧。
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final DateTimeFormatter CREATED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String VALIDITY_LABEL = "长期有效";
    private static final Set<String> SHELF_VALUES = Set.of("pending", "on", "off", "banned");
    private static final Set<String> SELL_VALUES = Set.of("free", "paid", "encrypted", "specified");
    private static final int RELATION_LIMIT = 20;

    private final ActivityRepository activityRepository;
    private final LiveProviderRegistry providerRegistry;
    private final ActivityOwnerRepository ownerRepository;
    private final ActivityProductRepository productRepository;
    private final ActivityAuditRepository auditRepository;
    private final SysUserAccountRepository userRepository;

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

        OpsDraft ops = draftOps(request);

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
        entity.setShelfStatus(ops.shelfStatus());
        entity.setRiskStatus("pending");
        entity.setSellType(ops.sellType());
        entity.setValidityLabel(VALIDITY_LABEL);

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
        saveOwners(entity.getId(), ops.ownerIds());
        saveProducts(entity.getId(), ops.products());
        return new CreateActivityResponse(entity.getId());
    }

    public ActivityListResponse list(
            int page,
            int pageSize,
            String keyword,
            Integer state,
            String provider,
            String shelfStatus,
            String startFrom,
            String startTo) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
        Integer stateFilter = (state != null && state == 0) ? null : state;
        String providerFilter = (provider == null || provider.isBlank()) ? null : provider.trim();
        String keywordFilter = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        String shelfFilter = normalizeShelfFilter(shelfStatus);
        String startFromBound = dayStart(startFrom, "startFrom");
        String startToBound = dayEnd(startTo, "startTo");
        if (startFromBound != null && startToBound != null && startToBound.compareTo(startFromBound) < 0) {
            throw new IllegalArgumentException("startTo 不能早于 startFrom");
        }

        Page<ActivityEntity> result = activityRepository.search(
                providerFilter,
                stateFilter,
                keywordFilter,
                shelfFilter,
                startFromBound,
                startToBound,
                PageRequest.of(page - 1, pageSize));

        List<ActivityEntity> visible = result.getContent().stream()
                .filter(activity -> activity.getDeletedAt() == null)
                .toList();
        List<ActivitySummary> summaries = visible.stream().map(this::toSummary).toList();
        attachRelations(summaries);

        ActivityListResponse response = new ActivityListResponse();
        response.setTotal(result.getTotalElements());
        response.setList(summaries);
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
        detail.setCanBroadcast(BroadcastRules.canBroadcast(
                detail.getRiskStatus(), detail.getShelfStatus(), detail.getState()));
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
        entity.setDeletedAt(LocalDateTime.now());
        activityRepository.save(entity);
        return new DeleteActivityResponse(entity.getId());
    }

    @Transactional
    public ShelfResponse shelf(String id, ShelfRequest request) {
        if (request == null || request.getListed() == null) {
            throw new IllegalArgumentException("listed 不能为空");
        }
        ActivityEntity entity = requireActivity(id);
        String current = entity.getShelfStatus();
        if ("banned".equals(current)) {
            throw new IllegalArgumentException("强制封禁不可上下架");
        }
        if (request.getListed()) {
            if ("on".equals(current)) {
                throw new IllegalArgumentException("已经上架");
            }
            if (!"pending".equals(current) && !"off".equals(current)) {
                throw new IllegalArgumentException("当前状态不可上架");
            }
            entity.setShelfStatus("on");
        } else {
            if (!"on".equals(current)) {
                throw new IllegalArgumentException("只有已上架可以下架");
            }
            entity.setShelfStatus("off");
        }
        activityRepository.save(entity);
        return new ShelfResponse(entity.getId(), entity.getShelfStatus());
    }

    public AuditListResponse audits(String id) {
        ActivityEntity entity = requireActivity(id);
        AuditListResponse response = new AuditListResponse();
        List<AuditRecordItem> list = new ArrayList<>();
        for (ActivityAuditEntity row : auditRepository.findByActivityIdOrderByCreatedAtDesc(entity.getId())) {
            AuditRecordItem item = new AuditRecordItem();
            item.setId(row.getId());
            item.setResult(row.getResult());
            if ("rejected".equals(row.getResult())) {
                item.setReason(row.getReason());
            }
            item.setOperatorId(row.getOperatorId() == null ? null : Long.toString(row.getOperatorId()));
            item.setOperatorName(row.getOperatorName());
            if (row.getCreatedAt() != null) {
                item.setCreatedAt(row.getCreatedAt().format(CREATED_AT));
            }
            list.add(item);
        }
        response.setList(list);
        return response;
    }

    @Transactional
    public AuditResultResponse audit(String id, AuditRequest request, long operatorId) {
        if (request == null || request.getResult() == null || request.getResult().isBlank()) {
            throw new IllegalArgumentException("审核结果不能为空");
        }
        String result = request.getResult().trim();
        if (!"passed".equals(result) && !"rejected".equals(result)) {
            throw new IllegalArgumentException("审核结果无效");
        }
        String reason = null;
        if ("rejected".equals(result)) {
            reason = request.getReason() == null ? "" : request.getReason().trim();
            if (reason.isEmpty() || reason.length() > 200) {
                throw new IllegalArgumentException("驳回理由须为1～200字");
            }
        } else if (request.getReason() != null && !request.getReason().isBlank()) {
            throw new IllegalArgumentException("通过时不要填写理由");
        }

        ActivityEntity entity = requireActivity(id);
        SysUserAccountEntity operator = userRepository.findById(operatorId)
                .orElseThrow(() -> new IllegalArgumentException("当前员工不存在"));

        if ("rejected".equals(result)) {
            entity.setRiskStatus("rejected");
            entity.setShelfStatus("banned");
        } else {
            if ("banned".equals(entity.getShelfStatus())) {
                entity.setShelfStatus("pending");
            }
            entity.setRiskStatus("passed");
        }
        activityRepository.save(entity);

        ActivityAuditEntity row = new ActivityAuditEntity();
        row.setActivityId(entity.getId());
        row.setResult(result);
        row.setReason(reason);
        row.setOperatorId(operator.getId());
        row.setOperatorName(operator.getUserName() == null ? "" : operator.getUserName());
        auditRepository.save(row);
        return new AuditResultResponse(entity.getId(), entity.getRiskStatus(), entity.getShelfStatus());
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
        ActivityEntity entity = activityRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
        if (entity.getDeletedAt() != null) {
            throw new IllegalArgumentException("直播不存在");
        }
        return entity;
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
        summary.setShelfStatus(entity.getShelfStatus());
        summary.setRiskStatus(entity.getRiskStatus());
        summary.setSellType(entity.getSellType());
        summary.setValidityLabel(entity.getValidityLabel());
        summary.setCanBroadcast(BroadcastRules.canBroadcast(
                entity.getRiskStatus(), entity.getShelfStatus(), entity.getState()));
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
        detail.setShelfStatus(base.getShelfStatus());
        detail.setRiskStatus(base.getRiskStatus());
        detail.setSellType(base.getSellType());
        detail.setValidityLabel(base.getValidityLabel());
        detail.setCanBroadcast(base.getCanBroadcast());
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
        attachRelations(List.of(detail));
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

    private OpsDraft draftOps(CreateActivityRequest request) {
        if (request.getListed() == null) {
            throw new IllegalArgumentException("listed 不能为空");
        }
        String sellType = request.getSellType() == null ? "" : request.getSellType().trim();
        if (!SELL_VALUES.contains(sellType)) {
            throw new IllegalArgumentException("sellType 无效");
        }
        List<Long> ownerIds = parseOwnerIds(request.getOwnerIds());
        List<ProductSnap> products = parseProducts(request.getProducts());
        return new OpsDraft(request.getListed() ? "on" : "pending", sellType, ownerIds, products);
    }

    private List<Long> parseOwnerIds(List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (String raw : rawIds) {
            ids.add(parseUserId(raw));
        }
        if (ids.size() > RELATION_LIMIT) {
            throw new IllegalArgumentException("归属人不能超过" + RELATION_LIMIT + "个");
        }
        Map<Long, SysUserAccountEntity> found = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(SysUserAccountEntity::getId, user -> user, (a, b) -> a));
        for (Long id : ids) {
            SysUserAccountEntity user = found.get(id);
            if (user == null || user.getRsStatus() == null || user.getRsStatus() != 1) {
                throw new IllegalArgumentException("归属人无效");
            }
        }
        return List.copyOf(ids);
    }

    private static long parseUserId(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (!text.matches("[1-9]\\d{0,18}")) {
            throw new IllegalArgumentException("归属人无效");
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("归属人无效");
        }
    }

    private static List<ProductSnap> parseProducts(List<CreateActivityRequest.ProductBinding> rawProducts) {
        if (rawProducts == null || rawProducts.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<String, String> bySpu = new LinkedHashMap<>();
        for (CreateActivityRequest.ProductBinding item : rawProducts) {
            if (item == null) {
                throw new IllegalArgumentException("关联商品无效");
            }
            String spuId = item.getSpuId() == null ? "" : item.getSpuId().trim();
            if (!spuId.matches("[1-9]\\d{0,31}")) {
                throw new IllegalArgumentException("关联商品无效");
            }
            if (bySpu.containsKey(spuId)) {
                throw new IllegalArgumentException("关联商品重复");
            }
            String name = item.getProductName() == null ? "" : item.getProductName().trim();
            if (name.isEmpty() || name.length() > 128) {
                throw new IllegalArgumentException("商品名称须为1～128字");
            }
            bySpu.put(spuId, name);
        }
        if (bySpu.size() > RELATION_LIMIT) {
            throw new IllegalArgumentException("关联商品不能超过" + RELATION_LIMIT + "个");
        }
        List<ProductSnap> products = new ArrayList<>();
        bySpu.forEach((spuId, name) -> products.add(new ProductSnap(spuId, name)));
        return products;
    }

    private void saveOwners(String activityId, List<Long> ownerIds) {
        if (ownerIds.isEmpty()) {
            return;
        }
        List<ActivityOwnerEntity> rows = new ArrayList<>();
        int order = 0;
        for (Long userId : ownerIds) {
            ActivityOwnerEntity row = new ActivityOwnerEntity();
            row.setId(new ActivityOwnerId(activityId, userId));
            row.setSortOrder(order++);
            rows.add(row);
        }
        ownerRepository.saveAll(rows);
    }

    private void saveProducts(String activityId, List<ProductSnap> products) {
        if (products.isEmpty()) {
            return;
        }
        List<ActivityProductEntity> rows = new ArrayList<>();
        int order = 0;
        for (ProductSnap product : products) {
            ActivityProductEntity row = new ActivityProductEntity();
            row.setId(new ActivityProductId(activityId, product.spuId()));
            row.setProductName(product.productName());
            row.setSortOrder(order++);
            rows.add(row);
        }
        productRepository.saveAll(rows);
    }

    private void attachRelations(List<ActivitySummary> summaries) {
        if (summaries.isEmpty()) {
            return;
        }
        List<String> ids = summaries.stream().map(ActivitySummary::getId).toList();
        Map<String, List<ActivityOwnerEntity>> owners = ownerRepository.findByActivityIds(ids).stream()
                .collect(Collectors.groupingBy(row -> row.getId().getActivityId(), LinkedHashMap::new, Collectors.toList()));
        Map<String, List<ActivityProductEntity>> products = productRepository.findByActivityIds(ids).stream()
                .collect(Collectors.groupingBy(row -> row.getId().getActivityId(), LinkedHashMap::new, Collectors.toList()));

        Set<Long> userIds = owners.values().stream()
                .flatMap(List::stream)
                .map(row -> row.getId().getUserId())
                .collect(Collectors.toSet());
        Map<Long, String> names = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(SysUserAccountEntity::getId, SysUserAccountEntity::getUserName, (a, b) -> a));

        for (ActivitySummary summary : summaries) {
            List<ActivityPerson> people = new ArrayList<>();
            for (ActivityOwnerEntity row : owners.getOrDefault(summary.getId(), List.of())) {
                String name = names.get(row.getId().getUserId());
                if (name == null) {
                    continue;
                }
                ActivityPerson person = new ActivityPerson();
                person.setId(Long.toString(row.getId().getUserId()));
                person.setUserName(name);
                people.add(person);
            }
            summary.setOwners(people);

            List<ActivityProductView> views = new ArrayList<>();
            for (ActivityProductEntity row : products.getOrDefault(summary.getId(), List.of())) {
                ActivityProductView view = new ActivityProductView();
                view.setSpuId(row.getId().getSpuId());
                view.setProductName(row.getProductName());
                views.add(view);
            }
            summary.setProducts(views);
        }
    }

    private static String normalizeShelfFilter(String shelfStatus) {
        if (shelfStatus == null || shelfStatus.isBlank()) {
            return null;
        }
        String value = shelfStatus.trim();
        if (!SHELF_VALUES.contains(value)) {
            throw new IllegalArgumentException("shelfStatus 无效");
        }
        return value;
    }

    private static String dayStart(String day, String field) {
        LocalDate date = parseDay(day, field);
        return date == null ? null : date + " 00:00:00";
    }

    private static String dayEnd(String day, String field) {
        LocalDate date = parseDay(day, field);
        return date == null ? null : date + " 23:59:59";
    }

    private static LocalDate parseDay(String day, String field) {
        if (day == null || day.isBlank()) {
            return null;
        }
        String text = day.trim();
        try {
            return LocalDate.parse(text, DAY);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(field + " 格式须为 yyyy-MM-dd");
        }
    }

    private record OpsDraft(String shelfStatus, String sellType, List<Long> ownerIds, List<ProductSnap> products) {
    }

    private record ProductSnap(String spuId, String productName) {
    }
}
