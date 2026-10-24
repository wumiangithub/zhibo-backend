package com.zhibo.admin.activity;

import com.zhibo.activity.ActivityAuditEntity;
import com.zhibo.activity.ActivityAuditRepository;
import com.zhibo.activity.ActivityCoursewareEntity;
import com.zhibo.activity.ActivityCoursewareId;
import com.zhibo.activity.ActivityCoursewareRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityMemberEntity;
import com.zhibo.activity.ActivityMemberId;
import com.zhibo.activity.ActivityMemberRepository;
import com.zhibo.activity.ActivityMemberRole;
import com.zhibo.activity.ActivityOwnerEntity;
import com.zhibo.activity.ActivityOwnerId;
import com.zhibo.activity.ActivityOwnerRepository;
import com.zhibo.activity.ActivityProductEntity;
import com.zhibo.activity.ActivityProductId;
import com.zhibo.activity.ActivityProductRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.activity.dto.AcceptActivityMemberRequest;
import com.zhibo.admin.activity.dto.AddActivityMembersRequest;
import com.zhibo.admin.activity.dto.DeleteActivityMembersRequest;
import com.zhibo.admin.activity.dto.AcceptActivityMemberResponse;
import com.zhibo.admin.activity.dto.ActivityCoursewareView;
import com.zhibo.admin.activity.dto.ActivityDetailResponse;
import com.zhibo.admin.activity.dto.ActivityListResponse;
import com.zhibo.admin.activity.dto.ActivityMemberItem;
import com.zhibo.admin.activity.dto.ActivityMemberListResponse;
import com.zhibo.admin.activity.dto.ActivityPerson;
import com.zhibo.admin.activity.dto.ActivityProductView;
import com.zhibo.admin.activity.dto.UpdateActivityMemberRequest;
import com.zhibo.admin.activity.dto.ActivityPushStreamResponse;
import com.zhibo.admin.activity.dto.ActivityStreamStatusResponse;
import com.zhibo.admin.activity.dto.ActivityRecordDownloadResponse;
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
import com.zhibo.admin.activity.dto.PlaybackUpdateRequest;
import com.zhibo.admin.activity.dto.SalesUpdateRequest;
import com.zhibo.admin.activity.dto.SalesUpdateResponse;
import com.zhibo.admin.activity.dto.ShelfRequest;
import com.zhibo.admin.activity.dto.ShelfResponse;
import com.zhibo.admin.activity.dto.StartActivityResponse;
import com.zhibo.admin.activity.dto.UpdateActivityRequest;
import com.zhibo.admin.auth.AuthService;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.material.MaterialService;
import com.zhibo.activity.ActivityReady;
import com.zhibo.activity.RecordDemandFinalizeService;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.CreateRoomCommand;
import com.zhibo.liveprovider.dto.CreateRoomResult;
import com.zhibo.liveprovider.dto.ProviderRecordItem;
import com.zhibo.liveprovider.dto.ProviderRecordList;
import com.zhibo.liveprovider.dto.ProviderRoomSnapshot;
import com.zhibo.liveprovider.dto.PushStreamInfo;
import com.zhibo.liveprovider.dto.UpdateRoomCommand;
import com.zhibo.livereport.LiveReportEntity;
import com.zhibo.livereport.LiveReportRepository;
import com.zhibo.material.MaterialAudit;
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.zhibo.common.UuidIds;
import java.util.stream.Collectors;

/**
 * 活动业务：本站落库 + 经 {@link LiveProvider} 调云侧。
 */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private static final DateTimeFormatter CREATED_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 活动起止时间（与接口约定一致） */
    private static final DateTimeFormatter ACTIVITY_DATE_TIME = CREATED_TIME;
    private static final java.time.ZoneId SHANGHAI = java.time.ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String VALIDITY_LONG = "长期有效";
    private static final Set<String> SHELF_VALUES = Set.of("pending", "on", "off", "banned");
    private static final Set<String> SELL_VALUES = Set.of("free", "paid", "encrypted", "specified");
    private static final Set<String> ACCESS_VALIDITY = Set.of("long", "days");
    private static final Set<String> PLAYBACK_VALIDITY = Set.of("forever", "days");
    private static final int RELATION_LIMIT = 20;
    private static final int COURSEWARE_LIMIT = 100;
    /** 微吼 get-role-url 身份邮箱后缀；与 HostService 一致 */
    private static final String ROLE_EMAIL_DOMAIN = "@qkzb.com";
    /** 微吼 get-role-url nickname 上限 */
    private static final int VHALL_NICKNAME_MAX = 30;
    private final ActivityRepository activityRepository;
    private final HostPasscodeService hostPasscodeService;
    private final LiveProviderRegistry providerRegistry;
    private final ActivityOwnerRepository ownerRepository;
    private final ActivityProductRepository productRepository;
    private final ActivityAuditRepository auditRepository;
    private final ActivityMemberRepository memberRepository;
    private final ActivityCoursewareRepository coursewareRepository;
    private final MaterialRepository materialRepository;
    private final SysUserAccountRepository userRepository;
    private final SellPasswordCryptoService sellPasswordCrypto;
    private final LiveReportRepository liveReportRepository;
    private final RecordDemandFinalizeService recordDemandFinalizeService;

    @Transactional
    public CreateActivityResponse create(CreateActivityRequest request, long operatorId) {
        if (request.getListed() != null) {
            throw new IllegalArgumentException("请改用 autoList，不再支持 listed");
        }
        if (request.getAutoList() == null) {
            throw new IllegalArgumentException("autoList 不能为空");
        }

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
        requireStartTimeAfterNow(request.getStartTime());
        validateOptionalEndTime(request.getEndTime(), request.getStartTime());

        String introduction = trimToNull(request.getIntroduction());
        if (introduction != null && introduction.length() > 256) {
            throw new IllegalArgumentException("简介不能超过256个字");
        }

        Integer type = request.getType();
        if (type == null || type < 1 || type > 4) {
            throw new IllegalArgumentException("直播类型无效");
        }

        List<Long> hostIds = parseEmployeeIds(request.getHostIds(), "主播", true);
        List<Long> ownerIds = parseEmployeeIds(request.getOwnerIds(), "归属人", false);
        List<ProductSnap> products = parseProducts(request.getProducts());
        List<CoursewareSnap> coursewares = parseCoursewares(request.getCoursewareMaterialIds());
        String reportId = trimToNull(request.getReportId());
        if (reportId == null) {
            throw new IllegalArgumentException("reportId 不能为空");
        }
        LiveReportEntity boundReport = requirePassedReport(reportId);
        assertHostMatchesReport(boundReport, hostIds);
        SysUserAccountEntity operator = requireOperator(operatorId);

        RecordCreatePlan recordPlan = planRecordCreate(type, request, liveProvider);
        Boolean isTiming = type == 4
                ? (request.getIsTiming() == null ? Boolean.TRUE : request.getIsTiming())
                : null;

        CreateRoomResult room = null;
        if (recordPlan.createNow()) {
            CreateRoomCommand createCmd = new CreateRoomCommand(
                    title,
                    request.getStartTime().trim(),
                    type,
                    introduction,
                    recordPlan.recordId(),
                    trimToNull(request.getCoverUrl()),
                    request.getUltraLowLatency(),
                    isTiming
            );
            room = liveProvider.create(createCmd);
        }

        ActivityEntity entity = new ActivityEntity();
        entity.setId(UuidIds.newId());
        entity.setReportId(boundReport.getId());
        entity.setProvider(provider);
        entity.setProviderRoomId(room == null ? null : room.providerRoomId());
        entity.setTitle(title);
        entity.setIntroduction(introduction);
        entity.setStartTime(request.getStartTime().trim());
        entity.setEndTime(trimToNull(request.getEndTime()));
        entity.setType(type);
        entity.setReady(recordPlan.ready());
        entity.setRecordMaterialId(recordPlan.recordMaterialId());
        entity.setIsTiming(isTiming);
        entity.setState(type == 4 ? 4 : 2);
        entity.setViewerStyle(normalizeViewerStyle(request.getViewerStyle()));
        entity.setUltraLowLatency(request.getUltraLowLatency() == null ? Boolean.TRUE : request.getUltraLowLatency());
        entity.setVirtualAvatar(request.getVirtualAvatar());
        entity.setCoverUrl(trimToNull(request.getCoverUrl()));
        entity.setWarmupType(trimToNull(request.getWarmupType()));
        entity.setWarmupUrl(trimToNull(request.getWarmupUrl()));
        entity.setPromoCoverUrl(trimToNull(request.getPromoCoverUrl()));
        entity.setDetailHtml(trimToNull(request.getDetailHtml()));
        entity.setAutoList(request.getAutoList());
        entity.setShelfStatus("pending");
        entity.setRiskStatus("pending");
        entity.setSellType("free");
        entity.setSellAlone(Boolean.TRUE);
        entity.setAccessValidityType("long");
        entity.setAccessValidityDays(null);
        entity.setValidityLabel(VALIDITY_LONG);
        entity.setPlaybackEnabled(Boolean.TRUE);
        entity.setPlaybackValidityType("forever");
        entity.setPlaybackValidityDays(null);
        entity.setPlaybackSpeedAllow(Boolean.TRUE);
        entity.setPlaybackFastForwardAllow(Boolean.TRUE);
        hostPasscodeService.assignOnCreate(entity);

        if (room != null) {
            try {
                ProviderRoomSnapshot snap = liveProvider.fetchRoom(room.providerRoomId());
                if (snap.state() != null) {
                    entity.setState(snap.state());
                }
                if (entity.getCoverUrl() == null && snap.coverUrl() != null) {
                    entity.setCoverUrl(snap.coverUrl());
                }
            } catch (RuntimeException ignored) {
                // 创建已成功；拉详情失败不挡落库
            }
        }

        // 必须先落库再写关联表；成员删除用 bulk @Modifying，不能 clear 掉未 flush 的活动
        activityRepository.saveAndFlush(entity);
        replaceHosts(entity.getId(), hostIds);
        replaceOwners(entity.getId(), ownerIds);
        replaceProducts(entity.getId(), products);
        replaceCoursewares(entity.getId(), coursewares);
        if (boundReport != null) {
            bumpBoundCount(boundReport, 1);
        }
        addAudit(entity.getId(), "submit", null, null, operator);
        return new CreateActivityResponse(entity.getId());
    }

    public ActivityListResponse list(
            int page,
            int pageSize,
            String keyword,
            Integer state,
            String states,
            String provider,
            String shelfStatus,
            String startFrom,
            String startTo,
            String sort,
            String order) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
        List<Integer> stateFilters = resolveStateFilters(state, states);
        boolean statesEmpty = stateFilters.isEmpty();
        List<Integer> statesParam = statesEmpty ? List.of(0) : stateFilters;
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
                statesEmpty,
                statesParam,
                keywordFilter,
                shelfFilter,
                startFromBound,
                startToBound,
                PageRequest.of(page - 1, pageSize, resolveListSort(sort, order)));

        List<ActivityEntity> visible = result.getContent().stream()
                .filter(activity -> activity.getDeletedTime() == null)
                .toList();
        List<ActivitySummary> summaries = visible.stream().map(this::toSummary).toList();
        attachOwnersAndProducts(summaries);

        ActivityListResponse response = new ActivityListResponse();
        response.setTotal(result.getTotalElements());
        response.setList(summaries);
        return response;
    }

    public ActivityDetailResponse detail(String id) {
        ActivityEntity entity = requireActivity(id);
        // 转码中：主动拉微吼音视频详情，防回调丢失
        syncPendingTranscodeFromProvider(entity);
        entity = requireActivity(id);
        ActivityDetailResponse detail = toDetail(entity);
        String roomId = trimToNull(entity.getProviderRoomId());
        if (roomId != null && ActivityReady.isOk(entity.getReady())) {
            LiveProvider liveProvider = providerRegistry.require(entity.getProvider());
            try {
                ProviderRoomSnapshot snap = liveProvider.fetchRoom(roomId);
                mergeCloudFields(detail, entity, snap);
                if (snap.state() != null && !snap.state().equals(entity.getState())) {
                    entity.setState(snap.state());
                    activityRepository.save(entity);
                }
            } catch (RuntimeException ignored) {
                // 以本站库为准
            }
        }
        detail.setCanBroadcast(BroadcastRules.canBroadcast(
                entity.getReady(),
                detail.getRiskStatus(),
                detail.getShelfStatus(),
                detail.getState()));
        return detail;
    }

    /**
     * 录播 {@code ready=0} 时调微吼 {@code videos/get-info} 查 {@code transcode_status}。
     * 成功/失败走与回调相同的收尾（create-demand / ready=2）；查询失败不挡详情。
     */
    private void syncPendingTranscodeFromProvider(ActivityEntity entity) {
        if (entity.getReady() == null || entity.getReady() != ActivityReady.PENDING) {
            return;
        }
        String materialId = trimToNull(entity.getRecordMaterialId());
        if (materialId == null) {
            return;
        }
        MaterialEntity material = materialRepository.findById(materialId)
                .filter(m -> m.getDeletedTime() == null)
                .orElse(null);
        if (material == null) {
            return;
        }
        String videoId = trimToNull(material.getVhallVideoId());
        if (videoId == null) {
            return;
        }
        try {
            LiveProvider liveProvider = providerRegistry.require(entity.getProvider());
            Integer status = liveProvider.getVideoTranscodeStatus(videoId);
            if (status == null) {
                return;
            }
            // 对齐进度回调：1 成功 / 2 转码中 / 3 失败
            if (status == MaterialService.VHALL_TRANSCODE_OK) {
                material.setVhallTranscodeStatus(MaterialService.VHALL_TRANSCODE_OK);
                material.setVhallTranscodeProgress(100);
                materialRepository.save(material);
                recordDemandFinalizeService.onMaterialTranscodeOk(materialId);
            } else if (status == MaterialService.VHALL_TRANSCODE_FAIL) {
                material.setVhallTranscodeStatus(MaterialService.VHALL_TRANSCODE_FAIL);
                materialRepository.save(material);
                recordDemandFinalizeService.onMaterialTranscodeFail(materialId);
            }
        } catch (RuntimeException ignored) {
            // 微吼查询失败不挡详情页
        }
    }

    @Transactional
    public CreateActivityResponse update(String id, UpdateActivityRequest request, long operatorId) {
        ActivityEntity entity = requireActivity(id);
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }

        boolean hasTitle = request.getTitle() != null && !request.getTitle().isBlank();
        boolean hasStart = request.getStartTime() != null && !request.getStartTime().isBlank();
        boolean hasIntro = request.getIntroduction() != null;
        boolean hasEnd = request.getEndTime() != null;
        boolean hasCover = request.getCoverUrl() != null;
        boolean hasDetail = request.getDetailHtml() != null;
        boolean hasAutoList = request.getAutoList() != null;
        boolean hasHosts = request.getHostIds() != null;
        boolean hasOwners = request.getOwnerIds() != null;
        boolean hasProducts = request.getProducts() != null;
        boolean hasCoursewares = request.getCoursewareMaterialIds() != null;
        boolean hasMaterialId = request.getRecordMaterialId() != null;

        if (!hasTitle && !hasStart && !hasIntro && !hasEnd && !hasCover && !hasDetail
                && !hasAutoList && !hasHosts && !hasOwners && !hasProducts && !hasCoursewares
                && !hasMaterialId) {
            throw new IllegalArgumentException("至少填写一个可改字段");
        }

        // 仅改关联商品：不拦审核中、不回 pending、不追加发起申请（直播带货 Tab）
        boolean productsOnly = hasProducts
                && !hasTitle && !hasStart && !hasIntro && !hasEnd && !hasCover && !hasDetail
                && !hasAutoList && !hasHosts && !hasOwners && !hasCoursewares && !hasMaterialId;
        if (productsOnly) {
            replaceProducts(entity.getId(), parseProducts(request.getProducts()));
            return new CreateActivityResponse(entity.getId());
        }

        if (isRiskPending(entity.getRiskStatus())) {
            throw new IllegalArgumentException("审核中不可编辑");
        }
        boolean reapply = isRiskReapply(entity.getRiskStatus());

        if (hasTitle) {
            String title = request.getTitle().trim();
            if (title.length() > 64) {
                throw new IllegalArgumentException("标题不能超过64个字");
            }
            entity.setTitle(title);
        }
        if (hasStart) {
            requireFullDateTime(request.getStartTime(), "开始时间");
            requireStartTimeAfterNow(request.getStartTime());
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
        if (hasCover) {
            entity.setCoverUrl(trimToNull(request.getCoverUrl()));
        }
        if (hasDetail) {
            entity.setDetailHtml(trimToNull(request.getDetailHtml()));
        }
        if (hasAutoList) {
            entity.setAutoList(request.getAutoList());
        }
        // viewerStyle / ultraLowLatency / 暖场等：创建后不可改，请求里带了忽略

        if (hasMaterialId) {
            replaceRecordMaterial(entity, trimToNull(request.getRecordMaterialId()));
        }

        boolean syncCloud = hasTitle || hasStart || hasIntro || hasCover;
        String roomId = trimToNull(entity.getProviderRoomId());
        if (syncCloud && roomId != null && ActivityReady.isOk(entity.getReady())) {
            LiveProvider liveProvider = providerRegistry.require(entity.getProvider());
            String coverForCloud = request.getCoverUrl() == null
                    ? null
                    : request.getCoverUrl().trim();
            liveProvider.update(roomId, new UpdateRoomCommand(
                    hasTitle ? entity.getTitle() : null,
                    hasStart ? entity.getStartTime() : null,
                    hasIntro ? entity.getIntroduction() : null,
                    coverForCloud
            ));
        }

        entity.setRiskStatus("pending");
        activityRepository.save(entity);

        if (hasHosts) {
            List<Long> hostIds = parseEmployeeIds(request.getHostIds(), "主播", true);
            String boundReportId = trimToNull(entity.getReportId());
            if (boundReportId != null) {
                LiveReportEntity report = liveReportRepository.findById(boundReportId)
                        .orElseThrow(() -> new IllegalArgumentException("直播报备不存在"));
                assertHostMatchesReport(report, hostIds);
            }
            replaceHosts(entity.getId(), hostIds);
        }
        if (hasOwners) {
            replaceOwners(entity.getId(), parseEmployeeIds(request.getOwnerIds(), "归属人", false));
        }
        if (hasProducts) {
            replaceProducts(entity.getId(), parseProducts(request.getProducts()));
        }
        if (hasCoursewares) {
            replaceCoursewares(entity.getId(), parseCoursewares(request.getCoursewareMaterialIds()));
        }
        if (reapply) {
            addAudit(entity.getId(), "submit", null, null, requireOperator(operatorId));
        }
        return new CreateActivityResponse(entity.getId());
    }

    @Transactional
    public CreateActivityResponse updatePlayback(String id, PlaybackUpdateRequest request) {
        if (request == null || request.getPlaybackEnabled() == null) {
            throw new IllegalArgumentException("playbackEnabled 不能为空");
        }
        ActivityEntity entity = requireActivity(id);
        boolean enabled = request.getPlaybackEnabled();
        entity.setPlaybackEnabled(enabled);
        if (enabled) {
            String type = request.getPlaybackValidityType() == null
                    ? "" : request.getPlaybackValidityType().trim();
            if (!PLAYBACK_VALIDITY.contains(type)) {
                throw new IllegalArgumentException("playbackValidityType 无效");
            }
            if (request.getPlaybackSpeedAllow() == null) {
                throw new IllegalArgumentException("playbackSpeedAllow 不能为空");
            }
            if (request.getPlaybackFastForwardAllow() == null) {
                throw new IllegalArgumentException("playbackFastForwardAllow 不能为空");
            }
            entity.setPlaybackValidityType(type);
            if ("days".equals(type)) {
                Integer days = request.getPlaybackValidityDays();
                if (days == null || days < 1) {
                    throw new IllegalArgumentException("playbackValidityDays 须为正整数");
                }
                entity.setPlaybackValidityDays(days);
            } else {
                entity.setPlaybackValidityDays(null);
            }
            entity.setPlaybackSpeedAllow(request.getPlaybackSpeedAllow());
            entity.setPlaybackFastForwardAllow(request.getPlaybackFastForwardAllow());
        } else {
            if (request.getPlaybackValidityType() != null && !request.getPlaybackValidityType().isBlank()) {
                String type = request.getPlaybackValidityType().trim();
                if (!PLAYBACK_VALIDITY.contains(type)) {
                    throw new IllegalArgumentException("playbackValidityType 无效");
                }
                entity.setPlaybackValidityType(type);
                if ("days".equals(type)) {
                    Integer days = request.getPlaybackValidityDays();
                    if (days == null || days < 1) {
                        throw new IllegalArgumentException("playbackValidityDays 须为正整数");
                    }
                    entity.setPlaybackValidityDays(days);
                } else {
                    entity.setPlaybackValidityDays(null);
                }
            }
            if (request.getPlaybackSpeedAllow() != null) {
                entity.setPlaybackSpeedAllow(request.getPlaybackSpeedAllow());
            }
            if (request.getPlaybackFastForwardAllow() != null) {
                entity.setPlaybackFastForwardAllow(request.getPlaybackFastForwardAllow());
            }
        }
        activityRepository.save(entity);
        return new CreateActivityResponse(entity.getId());
    }

    @Transactional
    public SalesUpdateResponse updateSales(String id, SalesUpdateRequest request) {
        if (request == null || request.getSellAlone() == null) {
            throw new IllegalArgumentException("sellAlone 不能为空");
        }
        if (request.getSellType() == null || request.getSellType().isBlank()) {
            throw new IllegalArgumentException("sellType 不能为空");
        }
        String sellType = request.getSellType().trim();
        if (!SELL_VALUES.contains(sellType)) {
            throw new IllegalArgumentException("sellType 无效");
        }

        ActivityEntity entity = requireActivity(id);
        entity.setSellAlone(request.getSellAlone());
        entity.setSellType(sellType);

        BigDecimal price = null;
        BigDecimal linePrice = null;
        String cipher = null;
        String accessType;
        Integer accessDays = null;

        if ("free".equals(sellType)) {
            accessType = "long";
        } else {
            accessType = request.getAccessValidityType() == null
                    ? "" : request.getAccessValidityType().trim();
            if (!ACCESS_VALIDITY.contains(accessType)) {
                throw new IllegalArgumentException("accessValidityType 无效");
            }
            if ("days".equals(accessType)) {
                accessDays = request.getAccessValidityDays();
                if (accessDays == null || accessDays < 1) {
                    throw new IllegalArgumentException("accessValidityDays 须为正整数");
                }
            }
        }

        if ("paid".equals(sellType)) {
            price = parseMoney(request.getPrice(), "商品价格", true);
            if (request.getLinePrice() != null && !request.getLinePrice().isBlank()) {
                linePrice = parseMoney(request.getLinePrice(), "划线价格", false);
                if (linePrice.compareTo(price) <= 0) {
                    throw new IllegalArgumentException("划线价格须大于商品价格");
                }
            }
        } else if ("encrypted".equals(sellType)) {
            String password = request.getPassword() == null ? "" : request.getPassword().trim();
            if (!password.matches("\\d{6}")) {
                throw new IllegalArgumentException("密码须为恰好6位数字");
            }
            cipher = sellPasswordCrypto.encrypt(password);
        }

        entity.setPrice(price);
        entity.setLinePrice(linePrice);
        entity.setSellPasswordCipher(cipher);
        entity.setAccessValidityType(accessType);
        entity.setAccessValidityDays(accessDays);
        entity.setValidityLabel(buildValidityLabel(accessType, accessDays));
        activityRepository.save(entity);
        return new SalesUpdateResponse(entity.getId(), entity.getSellType(), entity.getValidityLabel());
    }

    /**
     * 第三方推流开始直播。已软删由 {@link #requireActivity} 抛 400；未接入服务商由注册表抛 501。
     * 微吼失败原样向上抛。成功后把 info 的 webinar_state 写回本站 state，与结束直播同一做法。
     */
    @Transactional
    public StartActivityResponse startLive(String id) {
        ActivityEntity entity = requireActivity(id);
        LiveProvider liveProvider = providerRegistry.require(entity.getProvider());
        int state = liveProvider.start(entity.getProviderRoomId());
        entity.setState(state);
        activityRepository.save(entity);
        return new StartActivityResponse(entity.getId(), state);
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
    public DeleteActivityResponse delete(String id, long operatorId) {
        ActivityEntity entity = requireActivity(id);
        SysUserAccountEntity operator = requireOperator(operatorId);
        LiveProvider liveProvider = providerRegistry.require(entity.getProvider());
        liveProvider.delete(entity.getProviderRoomId());
        entity.setDeletedTime(LocalDateTime.now());
        entity.setDeletedBy(operator.getId());
        entity.setDeletedByName(operator.getUserName() == null ? "" : operator.getUserName());
        activityRepository.save(entity);
        if (entity.getReportId() != null && !entity.getReportId().isBlank()) {
            liveReportRepository.findById(entity.getReportId())
                    .ifPresent(report -> bumpBoundCount(report, -1));
        }
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
        List<ActivityAuditEntity> rows =
                auditRepository.findByActivityIdOrderByCreatedTimeDesc(entity.getId());
        Map<Long, String> deptByUser = departmentNamesOf(rows.stream()
                .map(ActivityAuditEntity::getOperatorId)
                .filter(Objects::nonNull)
                .filter(opId -> opId > 0)
                .collect(Collectors.toSet()));
        List<AuditRecordItem> list = new ArrayList<>();
        boolean hasSubmit = false;
        for (ActivityAuditEntity row : rows) {
            AuditRecordItem item = toAuditItem(row, deptByUser.get(row.getOperatorId()));
            if ("submit".equals(item.getAction())) {
                hasSubmit = true;
            }
            list.add(item);
        }
        if (!hasSubmit) {
            list.add(syntheticSubmit(entity));
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
        if (request.getReason() != null && !request.getReason().isBlank()) {
            reason = request.getReason().trim();
            if (reason.length() > 200) {
                throw new IllegalArgumentException("理由不能超过200字");
            }
        }
        if ("rejected".equals(result) && (reason == null || reason.isEmpty())) {
            throw new IllegalArgumentException("驳回理由须为1～200字");
        }

        ActivityEntity entity = requireActivity(id);
        SysUserAccountEntity operator = requireOperator(operatorId);

        if ("rejected".equals(result)) {
            entity.setRiskStatus("rejected");
            entity.setShelfStatus("banned");
        } else {
            entity.setRiskStatus("passed");
            if (Boolean.TRUE.equals(entity.getAutoList())) {
                entity.setShelfStatus("on");
            } else if ("banned".equals(entity.getShelfStatus())) {
                entity.setShelfStatus("pending");
            }
        }
        activityRepository.save(entity);
        addAudit(entity.getId(), "audit", result, reason, operator);
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
                    item.createdTime(),
                    item.coverUrl(),
                    item.storage(),
                    item.saveTime()
            ));
        }
        response.setList(list);
        return response;
    }

    /**
     * 某场活动下一条回放的下载地址（微吼 check-download）。
     * {@code recordId} = listRecords 的 {@code list[].id}。
     * 微吼 status=0 时由服务商实现提交打包任务，本次仍返回检查结果。
     */
    public ActivityRecordDownloadResponse recordDownload(String id, long recordId) {
        if (recordId <= 0) {
            throw new IllegalArgumentException("recordId 无效");
        }
        ActivityEntity entity = requireActivity(id);
        LiveProvider liveProvider = providerRegistry.require(entity.getProvider());
        var info = liveProvider.checkRecordDownload(recordId);
        return new ActivityRecordDownloadResponse(
                info.recordId() != null ? info.recordId() : recordId,
                info.downloadUrl(),
                info.status(),
                info.progress()
        );
    }

    /**
     * 某场本站活动的 rtmp 推流信息。srt 与口令开播不走本方法。
     * 已软删由 {@link #requireActivity} 抛 400；未接入服务商由注册表抛 501。
     * 微吼失败原样向上抛，不吞掉。
     */
    public ActivityPushStreamResponse pushStream(String id) {
        ActivityEntity entity = requireActivity(id);
        LiveProvider liveProvider = providerRegistry.require(entity.getProvider());
        PushStreamInfo info = liveProvider.getPushStream(entity.getProviderRoomId());
        ActivityPushStreamResponse response = new ActivityPushStreamResponse();
        response.setStreamAddress(info.streamAddress());
        response.setStreamCode(info.streamCode());
        response.setExpireTime(info.expireTime());
        return response;
    }

    /**
     * 某场本站活动的第三方推流是否有流。srt 与口令开播不走本方法。
     * 已软删由 {@link #requireActivity} 抛 400；未接入服务商由注册表抛 501。
     * 微吼失败原样向上抛，不吞掉。
     */
    public ActivityStreamStatusResponse streamStatus(String id) {
        ActivityEntity entity = requireActivity(id);
        LiveProvider liveProvider = providerRegistry.require(entity.getProvider());
        int status = liveProvider.getStreamStatus(entity.getProviderRoomId());
        ActivityStreamStatusResponse response = new ActivityStreamStatusResponse();
        response.setStatus(status);
        return response;
    }

    public ActivityEntity requireActivity(String id) {
        String uuid = ActivityIds.requireUuid(id);
        ActivityEntity entity = activityRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
        if (entity.getDeletedTime() != null) {
            throw new IllegalArgumentException("直播不存在");
        }
        return entity;
    }

    private SysUserAccountEntity requireOperator(long operatorId) {
        return userRepository.findById(operatorId)
                .orElseThrow(() -> new IllegalArgumentException("当前员工不存在"));
    }

    private void addAudit(String activityId, String action, String result, String reason,
            SysUserAccountEntity operator) {
        ActivityAuditEntity row = new ActivityAuditEntity();
        row.setActivityId(activityId);
        row.setAction(action);
        row.setResult(result);
        row.setReason(reason);
        row.setOperatorId(operator.getId());
        row.setOperatorName(operator.getUserName() == null ? "" : operator.getUserName());
        auditRepository.save(row);
    }

    private AuditRecordItem toAuditItem(ActivityAuditEntity row, String departmentName) {
        AuditRecordItem item = new AuditRecordItem();
        item.setId(row.getId());
        String action = row.getAction();
        if (action == null || action.isBlank()) {
            action = "audit";
        }
        item.setAction(action);
        if ("submit".equals(action)) {
            item.setOperatorId(row.getOperatorId());
            item.setOperatorName(row.getOperatorName());
            item.setDepartmentName(departmentName);
            if (row.getCreatedTime() != null) {
                item.setCreatedTime(row.getCreatedTime().format(CREATED_TIME));
            }
            return item;
        }
        item.setResult(row.getResult());
        if (row.getReason() != null && !row.getReason().isBlank()) {
            item.setReason(row.getReason());
        }
        item.setOperatorId(row.getOperatorId());
        item.setOperatorName(row.getOperatorName());
        item.setDepartmentName(departmentName);
        if (row.getCreatedTime() != null) {
            item.setCreatedTime(row.getCreatedTime().format(CREATED_TIME));
        }
        return item;
    }

    /** 批量解析操作人部门展示名；无部门的 id 不入 map。 */
    private Map<Long, String> departmentNamesOf(Set<Long> operatorIds) {
        if (operatorIds == null || operatorIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> out = new HashMap<>();
        for (SysUserAccountEntity user : userRepository.findAllById(operatorIds)) {
            String dept = AuthService.resolveDepartmentName(user);
            if (dept != null && !dept.isBlank()) {
                out.put(user.getId(), dept);
            }
        }
        return out;
    }

    /** 历史活动没有 submit 时，按创建时间补一条「发起申请」，不落库。 */
    private AuditRecordItem syntheticSubmit(ActivityEntity entity) {
        AuditRecordItem item = new AuditRecordItem();
        item.setId("synthetic-submit-" + entity.getId());
        item.setAction("submit");
        item.setOperatorId(0L);
        item.setOperatorName("系统");
        if (entity.getCreatedTime() != null) {
            item.setCreatedTime(entity.getCreatedTime().format(CREATED_TIME));
        }
        return item;
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
        summary.setReady(entity.getReady() == null ? ActivityReady.OK : entity.getReady());
        summary.setRecordMaterialId(entity.getRecordMaterialId());
        summary.setViewerStyle(entity.getViewerStyle());
        if (entity.getCreatedTime() != null) {
            summary.setCreatedTime(entity.getCreatedTime().format(CREATED_TIME));
        }
        summary.setShelfStatus(entity.getShelfStatus());
        summary.setRiskStatus(entity.getRiskStatus());
        summary.setSellType(entity.getSellType());
        summary.setValidityLabel(entity.getValidityLabel());
        summary.setCanBroadcast(BroadcastRules.canBroadcast(
                entity.getReady(), entity.getRiskStatus(), entity.getShelfStatus(), entity.getState()));
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
        detail.setReady(base.getReady());
        detail.setRecordMaterialId(base.getRecordMaterialId());
        detail.setViewerStyle(base.getViewerStyle());
        detail.setCreatedTime(base.getCreatedTime());
        detail.setShelfStatus(base.getShelfStatus());
        detail.setRiskStatus(base.getRiskStatus());
        detail.setSellType(base.getSellType());
        detail.setValidityLabel(base.getValidityLabel());
        detail.setCanBroadcast(base.getCanBroadcast());
        detail.setProviderRoomId(entity.getProviderRoomId());
        detail.setIntroduction(entity.getIntroduction());
        detail.setEndTime(entity.getEndTime());
        detail.setUltraLowLatency(entity.getUltraLowLatency());
        detail.setIsTiming(entity.getIsTiming());
        detail.setVirtualAvatar(entity.getVirtualAvatar());
        detail.setWarmupType(entity.getWarmupType());
        detail.setWarmupUrl(entity.getWarmupUrl());
        detail.setPromoCoverUrl(entity.getPromoCoverUrl());
        detail.setDetailHtml(entity.getDetailHtml());
        detail.setAutoList(entity.getAutoList());
        detail.setSellAlone(entity.getSellAlone());
        if ("paid".equals(entity.getSellType())) {
            detail.setPrice(entity.getPrice());
            detail.setLinePrice(entity.getLinePrice());
        }
        detail.setAccessValidityType(entity.getAccessValidityType());
        if ("days".equals(entity.getAccessValidityType())) {
            detail.setAccessValidityDays(entity.getAccessValidityDays());
        }
        if ("encrypted".equals(entity.getSellType()) && entity.getSellPasswordCipher() != null) {
            try {
                detail.setPassword(sellPasswordCrypto.decrypt(entity.getSellPasswordCipher()));
            } catch (RuntimeException ignored) {
                // 密钥缺失或历史脏数据：不输出 password
            }
        }
        detail.setPlaybackEnabled(entity.getPlaybackEnabled());
        detail.setPlaybackValidityType(entity.getPlaybackValidityType());
        if ("days".equals(entity.getPlaybackValidityType())) {
            detail.setPlaybackValidityDays(entity.getPlaybackValidityDays());
        }
        detail.setPlaybackSpeedAllow(entity.getPlaybackSpeedAllow());
        detail.setPlaybackFastForwardAllow(entity.getPlaybackFastForwardAllow());
        attachReport(detail, entity);
        attachOwnersAndProducts(List.of(detail));
        attachHostsAndCoursewares(detail);
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

    /**
     * 对齐微吼：定时开始须晚于当前至少 2 分钟，避免云侧 512096。
     * 在调 LiveProvider 之前拦截。
     */
    static void requireStartTimeAfterNow(String startTime) {
        requireFullDateTime(startTime, "开始时间");
        LocalDateTime start = LocalDateTime.parse(startTime.trim(), ACTIVITY_DATE_TIME);
        LocalDateTime minStart = LocalDateTime.now(SHANGHAI).plusMinutes(2);
        if (start.isBefore(minStart)) {
            throw new IllegalArgumentException("开始时间须晚于当前时间至少 2 分钟");
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

    /** 待审核或未写状态时，直播间基本信息不可改。 */
    private static boolean isRiskPending(String status) {
        return status == null || status.isBlank() || "pending".equalsIgnoreCase(status.trim());
    }

    /** 已通过或已驳回后再保存，视为重新发起申请。 */
    private static boolean isRiskReapply(String status) {
        if (status == null) {
            return false;
        }
        String value = status.trim();
        return "passed".equalsIgnoreCase(value) || "rejected".equalsIgnoreCase(value);
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private List<Long> parseEmployeeIds(List<Long> rawIds, String label, boolean required) {
        if (rawIds == null || rawIds.isEmpty()) {
            if (required) {
                throw new IllegalArgumentException(label + "至少选择1人");
            }
            return List.of();
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long raw : rawIds) {
            if (raw == null || raw <= 0L) {
                throw new IllegalArgumentException(label + "无效");
            }
            ids.add(raw);
        }
        if (required && ids.isEmpty()) {
            throw new IllegalArgumentException(label + "至少选择1人");
        }
        if (ids.size() > RELATION_LIMIT) {
            throw new IllegalArgumentException(label + "不能超过" + RELATION_LIMIT + "个");
        }
        Map<Long, SysUserAccountEntity> found = userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(SysUserAccountEntity::getId, user -> user, (a, b) -> a));
        for (Long id : ids) {
            SysUserAccountEntity user = found.get(id);
            if (user == null || user.getRsStatus() == null || user.getRsStatus() != 1) {
                throw new IllegalArgumentException(label + "无效");
            }
        }
        return List.copyOf(ids);
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

    /**
     * 录播创建分流：已转码 → 立刻 create-demand；未转码 → 代上传/等待，本站先 ready=0。
     */
    private RecordCreatePlan planRecordCreate(
            Integer type, CreateActivityRequest request, LiveProvider liveProvider) {
        Long recordIdNum = request.getRecordId();
        if (recordIdNum != null && recordIdNum <= 0) {
            throw new IllegalArgumentException("recordId 无效");
        }
        // CreateRoomCommand / 微吼仍用字符串透传（素材路径的 vhallVideoId 同为数字文本）
        String recordId = recordIdNum == null ? null : Long.toString(recordIdNum);
        String recordMaterialId = trimToNull(request.getRecordMaterialId());
        if (type == null || type != 4) {
            return new RecordCreatePlan(ActivityReady.OK, null, recordId, true);
        }
        if (recordMaterialId != null && recordId != null) {
            throw new IllegalArgumentException("录播请只传 recordMaterialId 或 recordId 其一");
        }
        if (recordMaterialId != null) {
            MaterialEntity video = requireRecordVideoBase(recordMaterialId);
            ensureVhallUpload(video, liveProvider);
            Integer status = video.getVhallTranscodeStatus();
            String videoId = trimToNull(video.getVhallVideoId());
            if (status != null && status == MaterialService.VHALL_TRANSCODE_OK && videoId != null) {
                return new RecordCreatePlan(ActivityReady.OK, recordMaterialId, videoId, true);
            }
            return new RecordCreatePlan(ActivityReady.PENDING, recordMaterialId, null, false);
        }
        if (recordId == null) {
            throw new IllegalArgumentException("录播须填写 recordId 或 recordMaterialId");
        }
        return new RecordCreatePlan(ActivityReady.OK, null, recordId, true);
    }

    /** 录播素材基线：未删、视频、两审通过、有 URL（不要求已转码）。 */
    private MaterialEntity requireRecordVideoBase(String materialId) {
        MaterialEntity material = materialRepository.findById(materialId)
                .filter(item -> item.getDeletedTime() == null)
                .orElseThrow(() -> new IllegalArgumentException("录播素材不存在"));
        if (!"video".equals(material.getType())) {
            throw new IllegalArgumentException("录播须选择视频素材");
        }
        if (!MaterialAudit.bothPassed(material)) {
            throw new IllegalArgumentException("录播素材须已通过审核");
        }
        if (material.getUrl() == null || material.getUrl().isBlank()) {
            throw new IllegalArgumentException("录播素材暂无可用地址");
        }
        return material;
    }

    /**
     * 无 video_id，或转码失败：代调 create-by-url。
     * 已在转码中（status=0）不重复上传。
     */
    private void ensureVhallUpload(MaterialEntity video, LiveProvider liveProvider) {
        Integer status = video.getVhallTranscodeStatus();
        String videoId = trimToNull(video.getVhallVideoId());
        if (status != null && status == MaterialService.VHALL_TRANSCODE_PENDING && videoId != null) {
            return;
        }
        if (status != null && status == MaterialService.VHALL_TRANSCODE_OK && videoId != null) {
            return;
        }
        String newId = liveProvider.createVideoByUrl(video.getUrl().trim(), video.getName());
        if (newId == null || newId.isBlank()) {
            throw new IllegalArgumentException("微吼未返回 video_id");
        }
        video.setVhallVideoId(newId.trim());
        video.setVhallTranscodeProgress(0);
        video.setVhallTranscodeStatus(MaterialService.VHALL_TRANSCODE_PENDING);
        materialRepository.save(video);
    }

    /** 设置页替换录播视频：ready→0，按需再上传；等回调 create-demand。 */
    private void replaceRecordMaterial(ActivityEntity entity, String materialId) {
        if (entity.getType() == null || entity.getType() != 4) {
            throw new IllegalArgumentException("仅录播可替换视频素材");
        }
        if (materialId == null) {
            throw new IllegalArgumentException("recordMaterialId 不能为空");
        }
        MaterialEntity video = requireRecordVideoBase(materialId);
        LiveProvider liveProvider = providerRegistry.require(entity.getProvider());
        ensureVhallUpload(video, liveProvider);
        entity.setRecordMaterialId(materialId);
        entity.setReady(ActivityReady.PENDING);
        // 旧云侧房间暂留；新 create-demand 成功后再覆盖 provider_room_id
        Integer status = video.getVhallTranscodeStatus();
        String videoId = trimToNull(video.getVhallVideoId());
        if (status != null && status == MaterialService.VHALL_TRANSCODE_OK && videoId != null) {
            // 已转码：当场建点播
            CreateRoomResult room = liveProvider.create(new CreateRoomCommand(
                    entity.getTitle(),
                    entity.getStartTime(),
                    entity.getType(),
                    entity.getIntroduction(),
                    videoId,
                    entity.getCoverUrl(),
                    entity.getUltraLowLatency(),
                    entity.getIsTiming()
            ));
            entity.setProviderRoomId(room.providerRoomId());
            entity.setReady(ActivityReady.OK);
        }
    }

    private record RecordCreatePlan(int ready, String recordMaterialId, String recordId, boolean createNow) {
    }

    private List<CoursewareSnap> parseCoursewares(List<String> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String raw : rawIds) {
            String id = raw == null ? "" : raw.trim();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("课件无效");
            }
            ids.add(id);
        }
        if (ids.size() > COURSEWARE_LIMIT) {
            throw new IllegalArgumentException("课件不能超过" + COURSEWARE_LIMIT + "个");
        }
        Map<String, MaterialEntity> found = materialRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(MaterialEntity::getId, m -> m, (a, b) -> a));
        List<CoursewareSnap> snaps = new ArrayList<>();
        for (String id : ids) {
            MaterialEntity material = found.get(id);
            if (material == null || material.getDeletedTime() != null) {
                throw new IllegalArgumentException("课件无效");
            }
            snaps.add(new CoursewareSnap(
                    material.getId(),
                    material.getName(),
                    material.getUrl(),
                    material.getType()));
        }
        return snaps;
    }

    /**
     * 只全量覆盖主播行（{@link ActivityMemberRole#HOST}），不删助理 / 嘉宾。
     * 默认标签「讲师」，与角色设置里添加主播一致；昵称/头像快照员工资料。
     */
    private void replaceHosts(String activityId, List<Long> hostIds) {
        memberRepository.deleteByActivityIdAndRole(activityId, ActivityMemberRole.HOST.getCode());
        if (hostIds.isEmpty()) {
            return;
        }
        Map<Long, SysUserAccountEntity> users = userRepository.findAllById(hostIds).stream()
                .collect(Collectors.toMap(SysUserAccountEntity::getId, user -> user, (a, b) -> a));
        List<ActivityMemberEntity> rows = new ArrayList<>();
        int order = 0;
        for (Long userId : hostIds) {
            SysUserAccountEntity user = users.get(userId);
            ActivityMemberEntity row = new ActivityMemberEntity();
            row.setId(new ActivityMemberId(activityId, userId));
            row.setRole(ActivityMemberRole.HOST.getCode());
            row.setSortOrder(order++);
            row.setTag(ActivityMemberRole.HOST.getDefaultTag());
            if (user != null) {
                row.setNickname(snapshotMemberNickname(user));
                row.setAvatarUrl(AuthService.resolveAvatar(user));
            }
            rows.add(row);
        }
        memberRepository.saveAll(rows);
    }

    @Transactional(readOnly = true)
    public ActivityMemberListResponse listMembers(String activityId) {
        ActivityEntity entity = requireActivity(activityId);
        return toMemberList(memberRepository.findByActivityIds(List.of(entity.getId())));
    }

    /**
     * 更新一名已有开播成员的本场昵称、头像、标签。不改角色，不增删其他人。
     * 不改 {@code activity_owner}，不触发风控回 pending。
     * 有 {@code provider_room_id} 时调微吼 get-role-url，把本场昵称/头像推到云侧（返回的 page_url 丢弃）。
     */
    @Transactional
    public ActivityMemberListResponse updateMember(String activityId, UpdateActivityMemberRequest request) {
        ActivityEntity entity = requireActivity(activityId);
        if (request == null || request.getId() == null || request.getId() <= 0L) {
            throw new IllegalArgumentException("成员无效");
        }
        List<ActivityMemberEntity> existing = memberRepository.findByActivityIds(List.of(entity.getId()));
        ActivityMemberEntity target = null;
        for (ActivityMemberEntity row : existing) {
            if (request.getId().equals(row.getId().getUserId())) {
                target = row;
                break;
            }
        }
        if (target == null) {
            throw new IllegalArgumentException("成员无效");
        }
        SysUserAccountEntity user = userRepository.findById(request.getId())
                .orElseThrow(() -> new IllegalArgumentException("成员无效"));
        String nickname = trimToNullLimited(request.getNickname(), 10, "本场昵称");
        if (nickname == null) {
            nickname = snapshotMemberNickname(user);
        }
        String avatarUrl = trimToNullLimited(request.getAvatarUrl(), 512, "本场头像");
        if (avatarUrl == null) {
            avatarUrl = AuthService.resolveAvatar(user);
        }
        String tag = trimToNullLimited(request.getTag(), 32, "标签");
        if (tag == null) {
            tag = ActivityMemberRole.requireCode(target.getRole()).getDefaultTag();
        }
        target.setNickname(nickname);
        target.setAvatarUrl(avatarUrl);
        target.setTag(tag);
        memberRepository.save(target);
        syncMemberRoleProfileToProvider(entity, target);
        return toMemberList(existing);
    }

    /**
     * 增量添加开播成员。只插入请求里的人，不删除、不改已有行。
     * 不改 {@code activity_owner}，不触发风控回 pending。
     * 有 {@code provider_room_id} 时对每位新人调微吼 get-role-url，推送本场昵称/头像。
     */
    @Transactional
    public ActivityMemberListResponse addMembers(String activityId, AddActivityMembersRequest request) {
        ActivityEntity entity = requireActivity(activityId);
        List<ParsedMember> incoming = parseMembers(request == null ? null : request.getMembers());
        List<ActivityMemberEntity> existing = memberRepository.findByActivityIds(List.of(entity.getId()));
        Map<Long, ActivityMemberEntity> byUser = new LinkedHashMap<>();
        int[] counts = new int[4];
        int[] nextOrder = new int[4];
        for (ActivityMemberEntity row : existing) {
            byUser.put(row.getId().getUserId(), row);
            int role = row.getRole();
            if (role >= ActivityMemberRole.HOST.getCode() && role <= ActivityMemberRole.ASSISTANT.getCode()) {
                counts[role]++;
                nextOrder[role] = Math.max(nextOrder[role], row.getSortOrder() + 1);
            }
        }
        boolean hostLocked = trimToNull(entity.getReportId()) != null;
        List<ActivityMemberEntity> rows = new ArrayList<>();
        for (ParsedMember item : incoming) {
            if (byUser.containsKey(item.userId())) {
                throw new IllegalArgumentException("同一员工不能重复担任开播角色");
            }
            if (hostLocked && ActivityMemberRole.isHost(item.role())) {
                throw new IllegalArgumentException("已绑定直播报备不可修改");
            }
            counts[item.role()]++;
            if (counts[item.role()] > RELATION_LIMIT) {
                String label = ActivityMemberRole.requireCode(item.role()).getLabel();
                throw new IllegalArgumentException(label + "不能超过" + RELATION_LIMIT + "个");
            }
            ActivityMemberEntity row = new ActivityMemberEntity();
            row.setId(new ActivityMemberId(activityId, item.userId()));
            row.setRole(item.role());
            row.setSortOrder(nextOrder[item.role()]++);
            row.setNickname(item.nickname());
            row.setAvatarUrl(item.avatarUrl());
            String tag = item.tag();
            if (tag == null) {
                tag = ActivityMemberRole.requireCode(item.role()).getDefaultTag();
            }
            row.setTag(tag);
            rows.add(row);
            byUser.put(item.userId(), row);
        }
        memberRepository.saveAll(rows);
        for (ActivityMemberEntity row : rows) {
            syncMemberRoleProfileToProvider(entity, row);
        }
        List<ActivityMemberEntity> all = new ArrayList<>(existing);
        all.addAll(rows);
        all.sort(Comparator.comparingInt(ActivityMemberEntity::getRole)
                .thenComparingInt(ActivityMemberEntity::getSortOrder));
        return toMemberList(all);
    }

    /**
     * 按员工 id 删除开播成员。只删列出的人，其余不动。
     * 删完至少还要留 1 名主播。已绑报备时不能删主播。
     * 有 {@code provider_room_id} 时，对被删的嘉宾/助理调微吼 cancel-role；主播只落本站（官方不支持取消主持人）。
     */
    @Transactional
    public ActivityMemberListResponse deleteMembers(String activityId, DeleteActivityMembersRequest request) {
        ActivityEntity entity = requireActivity(activityId);
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            throw new IllegalArgumentException("ids 不能为空");
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long userId : request.getIds()) {
            if (userId == null || userId <= 0L) {
                throw new IllegalArgumentException("成员无效");
            }
            if (!ids.add(userId)) {
                throw new IllegalArgumentException("不能重复删除同一员工");
            }
        }
        List<ActivityMemberEntity> existing = memberRepository.findByActivityIds(List.of(entity.getId()));
        Map<Long, ActivityMemberEntity> byUser = new LinkedHashMap<>();
        int hostCount = 0;
        for (ActivityMemberEntity row : existing) {
            byUser.put(row.getId().getUserId(), row);
            if (ActivityMemberRole.isHost(row.getRole())) {
                hostCount++;
            }
        }
        boolean hostLocked = trimToNull(entity.getReportId()) != null;
        int removingHosts = 0;
        List<ActivityMemberEntity> toRemove = new ArrayList<>();
        for (Long userId : ids) {
            ActivityMemberEntity row = byUser.get(userId);
            if (row == null) {
                throw new IllegalArgumentException("成员无效");
            }
            if (ActivityMemberRole.isHost(row.getRole())) {
                removingHosts++;
                if (hostLocked) {
                    throw new IllegalArgumentException("已绑定直播报备不可修改");
                }
            }
            toRemove.add(row);
        }
        if (hostCount - removingHosts < 1) {
            throw new IllegalArgumentException("主播至少选择1人");
        }
        for (ActivityMemberEntity row : toRemove) {
            cancelMemberRoleOnProvider(entity, row);
        }
        for (Long userId : ids) {
            memberRepository.deleteById(new ActivityMemberId(activityId, userId));
        }
        List<ActivityMemberEntity> remaining = new ArrayList<>();
        for (ActivityMemberEntity row : existing) {
            if (!ids.contains(row.getId().getUserId())) {
                remaining.add(row);
            }
        }
        return toMemberList(remaining);
    }

    /**
     * 打开角色邀约链接：把当前登录员工加入本场 {@code activity_member}。
     * 邀约人只校验并回显，不写入成员表。同一人同一角色再次打开视为已加入。
     * 有 {@code provider_room_id} 时调微吼 get-role-url，推送本场昵称/头像（已加入也再推一次）。
     */
    @Transactional
    public AcceptActivityMemberResponse acceptMemberInvite(
            String activityId,
            long userId,
            AcceptActivityMemberRequest request) {
        ActivityEntity entity = requireActivity(activityId);
        if (request == null || request.getRole() == null || request.getInviterId() == null) {
            throw new IllegalArgumentException("邀约信息不完整");
        }
        ActivityMemberRole role = ActivityMemberRole.requireCode(request.getRole());
        if (request.getInviterId() <= 0L || userId <= 0L) {
            throw new IllegalArgumentException("邀约人无效");
        }
        SysUserAccountEntity invitee = requireActiveUser(userId, "成员无效");
        SysUserAccountEntity inviter = requireActiveUser(request.getInviterId(), "邀约人无效");

        List<ActivityMemberEntity> rows = memberRepository.findByActivityIds(List.of(entity.getId()));
        ActivityMemberEntity mine = null;
        int roleCount = 0;
        int nextOrder = 0;
        for (ActivityMemberEntity row : rows) {
            if (userId == row.getId().getUserId()) {
                mine = row;
            }
            if (row.getRole() == role.getCode()) {
                roleCount++;
                nextOrder = Math.max(nextOrder, row.getSortOrder() + 1);
            }
        }
        if (mine != null) {
            if (mine.getRole() == role.getCode()) {
                syncMemberRoleProfileToProvider(entity, mine);
                return acceptResponse(invitee, role, true, inviter);
            }
            String current = ActivityMemberRole.requireCode(mine.getRole()).getLabel();
            throw new IllegalArgumentException("你已是本场的" + current + "，不能再加入为" + role.getLabel());
        }
        if (role == ActivityMemberRole.HOST && trimToNull(entity.getReportId()) != null) {
            throw new IllegalArgumentException("已绑定直播报备，主播不能通过邀约加入");
        }
        if (roleCount >= RELATION_LIMIT) {
            throw new IllegalArgumentException(role.getLabel() + "不能超过" + RELATION_LIMIT + "个");
        }
        ActivityMemberEntity row = new ActivityMemberEntity();
        row.setId(new ActivityMemberId(entity.getId(), userId));
        row.setRole(role.getCode());
        row.setSortOrder(nextOrder);
        row.setTag(role.getDefaultTag());
        row.setNickname(snapshotMemberNickname(invitee));
        row.setAvatarUrl(AuthService.resolveAvatar(invitee));
        memberRepository.save(row);
        syncMemberRoleProfileToProvider(entity, row);
        return acceptResponse(invitee, role, false, inviter);
    }

    /**
     * 调微吼 get-role-url，用本场昵称/头像更新云侧角色资料；返回的 page_url 不用。
     * 尚无 {@code provider_room_id}（如录播未建场）则跳过。云侧异常抛出则事务回滚。
     */
    private void syncMemberRoleProfileToProvider(ActivityEntity entity, ActivityMemberEntity member) {
        String roomId = trimToNull(entity.getProviderRoomId());
        if (roomId == null) {
            return;
        }
        long userId = member.getId().getUserId();
        String email = userId + ROLE_EMAIL_DOMAIN;
        String nickname = trimToNull(member.getNickname());
        if (nickname == null) {
            nickname = "用户" + userId;
        } else if (nickname.length() > VHALL_NICKNAME_MAX) {
            nickname = nickname.substring(0, VHALL_NICKNAME_MAX);
        }
        String avatar = trimToNull(member.getAvatarUrl());
        providerRegistry.require(entity.getProvider())
                .buildRoleJoinUrl(roomId, member.getRole(), email, nickname, avatar);
    }

    /**
     * 调微吼 cancel-role，取消嘉宾/助理云侧身份。主播跳过（官方接口不含主持人）。
     * 尚无 {@code provider_room_id} 则跳过。云侧异常抛出则事务回滚（与 get-role-url 同步一致）。
     */
    private void cancelMemberRoleOnProvider(ActivityEntity entity, ActivityMemberEntity member) {
        if (ActivityMemberRole.isHost(member.getRole())) {
            return;
        }
        String roomId = trimToNull(entity.getProviderRoomId());
        if (roomId == null) {
            return;
        }
        String email = member.getId().getUserId() + ROLE_EMAIL_DOMAIN;
        providerRegistry.require(entity.getProvider()).cancelRole(roomId, email);
    }

    private AcceptActivityMemberResponse acceptResponse(
            SysUserAccountEntity invitee,
            ActivityMemberRole role,
            boolean alreadyMember,
            SysUserAccountEntity inviter) {
        AcceptActivityMemberResponse response = new AcceptActivityMemberResponse();
        response.setId(invitee.getId());
        response.setUserName(invitee.getUserName());
        response.setRole(role.getCode());
        response.setAlreadyMember(alreadyMember);
        response.setInviterName(inviter.getUserName());
        return response;
    }

    private SysUserAccountEntity requireActiveUser(long userId, String message) {
        return userRepository.findById(userId)
                .filter(user -> user.getRsStatus() != null && user.getRsStatus() == 1)
                .orElseThrow(() -> new IllegalArgumentException(message));
    }

    private List<ParsedMember> parseMembers(List<AddActivityMembersRequest.MemberBinding> raw) {
        if (raw == null) {
            throw new IllegalArgumentException("members 不能为空");
        }
        LinkedHashMap<Long, ParsedMember> byUser = new LinkedHashMap<>();
        int hostCount = 0;
        int assistantCount = 0;
        int guestCount = 0;
        for (AddActivityMembersRequest.MemberBinding item : raw) {
            if (item == null || item.getId() == null || item.getId() <= 0L) {
                throw new IllegalArgumentException("成员无效");
            }
            ActivityMemberRole role = ActivityMemberRole.requireCode(item.getRole());
            if (byUser.containsKey(item.getId())) {
                throw new IllegalArgumentException("同一员工不能重复担任开播角色");
            }
            if (role == ActivityMemberRole.HOST) {
                hostCount++;
            } else if (role == ActivityMemberRole.ASSISTANT) {
                assistantCount++;
            } else {
                guestCount++;
            }
            byUser.put(item.getId(), new ParsedMember(
                    item.getId(),
                    role.getCode(),
                    trimToNullLimited(item.getNickname(), 10, "本场昵称"),
                    trimToNullLimited(item.getAvatarUrl(), 512, "本场头像"),
                    trimToNullLimited(item.getTag(), 32, "标签")));
        }
        if (hostCount > RELATION_LIMIT) {
            throw new IllegalArgumentException("主播不能超过" + RELATION_LIMIT + "个");
        }
        if (assistantCount > RELATION_LIMIT) {
            throw new IllegalArgumentException("助理不能超过" + RELATION_LIMIT + "个");
        }
        if (guestCount > RELATION_LIMIT) {
            throw new IllegalArgumentException("嘉宾不能超过" + RELATION_LIMIT + "个");
        }
        Map<Long, SysUserAccountEntity> found = userRepository.findAllById(byUser.keySet()).stream()
                .collect(Collectors.toMap(SysUserAccountEntity::getId, user -> user, (a, b) -> a));
        List<ParsedMember> resolved = new ArrayList<>(byUser.size());
        for (ParsedMember item : byUser.values()) {
            SysUserAccountEntity user = found.get(item.userId());
            if (user == null || user.getRsStatus() == null || user.getRsStatus() != 1) {
                throw new IllegalArgumentException("成员无效");
            }
            String nickname = item.nickname();
            if (nickname == null) {
                nickname = snapshotMemberNickname(user);
            }
            String avatarUrl = item.avatarUrl();
            if (avatarUrl == null) {
                avatarUrl = AuthService.resolveAvatar(user);
            }
            resolved.add(new ParsedMember(item.userId(), item.role(), nickname, avatarUrl, item.tag()));
        }
        return List.copyOf(resolved);
    }

    /**
     * 写入成员表时未带本场昵称则快照员工姓名；产品展示上限 ≤10，超长截断。
     */
    private static String snapshotMemberNickname(SysUserAccountEntity user) {
        String name = trimToNull(user.getUserName());
        if (name == null) {
            return null;
        }
        return name.length() <= 10 ? name : name.substring(0, 10);
    }

    private ActivityMemberListResponse toMemberList(List<ActivityMemberEntity> rows) {
        Set<Long> userIds = rows.stream().map(row -> row.getId().getUserId()).collect(Collectors.toSet());
        Map<Long, SysUserAccountEntity> users = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(SysUserAccountEntity::getId, u -> u, (a, b) -> a));
        ActivityMemberListResponse response = new ActivityMemberListResponse();
        List<ActivityMemberItem> list = new ArrayList<>();
        for (ActivityMemberEntity row : rows) {
            SysUserAccountEntity user = users.get(row.getId().getUserId());
            if (user == null) {
                continue;
            }
            ActivityMemberItem item = new ActivityMemberItem();
            item.setId(user.getId());
            item.setUserName(user.getUserName());
            String headimgurl = trimToNull(user.getHeadimgurl());
            if (headimgurl != null) {
                item.setHeadimgurl(headimgurl);
            }
            String dept = AuthService.resolveDepartmentName(user);
            item.setDepartmentName(dept == null ? "" : dept);
            item.setRole(row.getRole());
            String nick = trimToNull(row.getNickname());
            item.setNickname(nick != null ? nick : user.getUserName());
            String avatar = trimToNull(row.getAvatarUrl());
            if (avatar != null) {
                item.setAvatarUrl(avatar);
            }
            String tag = trimToNull(row.getTag());
            if (tag == null && ActivityMemberRole.isHost(row.getRole())) {
                // 旧数据从 activity_host 迁入时 tag 为空，展示与新建默认一致
                tag = ActivityMemberRole.HOST.getDefaultTag();
            }
            item.setTag(tag == null ? "" : tag);
            list.add(item);
        }
        response.setList(list);
        return response;
    }

    private static String trimToNullLimited(String raw, int max, String label) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        if (value.length() > max) {
            throw new IllegalArgumentException(label + "不能超过" + max + "字");
        }
        return value;
    }

    private record ParsedMember(Long userId, int role, String nickname, String avatarUrl, String tag) {
    }

    private void replaceOwners(String activityId, List<Long> ownerIds) {
        ownerRepository.deleteByIdActivityId(activityId);
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

    private void replaceProducts(String activityId, List<ProductSnap> products) {
        productRepository.deleteByIdActivityId(activityId);
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

    private void replaceCoursewares(String activityId, List<CoursewareSnap> coursewares) {
        coursewareRepository.deleteByIdActivityId(activityId);
        if (coursewares.isEmpty()) {
            return;
        }
        List<ActivityCoursewareEntity> rows = new ArrayList<>();
        int order = 0;
        for (CoursewareSnap item : coursewares) {
            ActivityCoursewareEntity row = new ActivityCoursewareEntity();
            row.setId(new ActivityCoursewareId(activityId, item.materialId()));
            row.setName(item.name());
            row.setUrl(item.url());
            row.setType(item.type());
            row.setSortOrder(order++);
            rows.add(row);
        }
        coursewareRepository.saveAll(rows);
    }

    private void attachOwnersAndProducts(List<ActivitySummary> summaries) {
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
        Map<Long, SysUserAccountEntity> users = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(SysUserAccountEntity::getId, u -> u, (a, b) -> a));

        for (ActivitySummary summary : summaries) {
            List<ActivityPerson> people = new ArrayList<>();
            for (ActivityOwnerEntity row : owners.getOrDefault(summary.getId(), List.of())) {
                SysUserAccountEntity user = users.get(row.getId().getUserId());
                if (user == null) {
                    continue;
                }
                people.add(toPerson(user));
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

    private void attachHostsAndCoursewares(ActivityDetailResponse detail) {
        List<ActivityMemberEntity> hosts = memberRepository.findByActivityIds(List.of(detail.getId())).stream()
                .filter(row -> ActivityMemberRole.isHost(row.getRole()))
                .toList();
        Set<Long> userIds = hosts.stream().map(row -> row.getId().getUserId()).collect(Collectors.toSet());
        Map<Long, SysUserAccountEntity> users = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(SysUserAccountEntity::getId, u -> u, (a, b) -> a));
        List<ActivityPerson> people = new ArrayList<>();
        for (ActivityMemberEntity row : hosts) {
            SysUserAccountEntity user = users.get(row.getId().getUserId());
            if (user == null) {
                continue;
            }
            people.add(toPerson(user));
        }
        detail.setHosts(people);

        List<ActivityCoursewareEntity> rows = coursewareRepository.findByActivityIds(List.of(detail.getId()));
        Set<String> materialIds = rows.stream().map(row -> row.getId().getMaterialId()).collect(Collectors.toSet());
        Map<String, MaterialEntity> materials = materialIds.isEmpty()
                ? Map.of()
                : materialRepository.findAllById(materialIds).stream()
                .collect(Collectors.toMap(MaterialEntity::getId, m -> m, (a, b) -> a));
        List<ActivityCoursewareView> views = new ArrayList<>();
        for (ActivityCoursewareEntity row : rows) {
            MaterialEntity material = materials.get(row.getId().getMaterialId());
            if (material == null || material.getDeletedTime() != null) {
                continue;
            }
            ActivityCoursewareView view = new ActivityCoursewareView();
            view.setId(row.getId().getMaterialId());
            view.setName(row.getName());
            view.setType(row.getType());
            view.setUrl(row.getUrl());
            views.add(view);
        }
        detail.setCoursewares(views);
    }

    private static ActivityPerson toPerson(SysUserAccountEntity user) {
        ActivityPerson person = new ActivityPerson();
        person.setId(user.getId());
        person.setUserName(user.getUserName());
        person.setAvatar(AuthService.resolveAvatar(user));
        person.setDepartmentName(AuthService.resolveDepartmentName(user));
        return person;
    }

    private static BigDecimal parseMoney(String raw, String label, boolean required) {
        if (raw == null || raw.isBlank()) {
            if (required) {
                throw new IllegalArgumentException(label + "不能为空");
            }
            return null;
        }
        String text = raw.trim();
        BigDecimal value;
        try {
            value = new BigDecimal(text);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(label + "无效");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(label + "不能为负");
        }
        if (value.scale() > 2) {
            throw new IllegalArgumentException(label + "最多两位小数");
        }
        return value.setScale(2, RoundingMode.UNNECESSARY);
    }

    private static String buildValidityLabel(String accessType, Integer days) {
        if ("days".equals(accessType) && days != null && days > 0) {
            return days + "天有效";
        }
        return VALIDITY_LONG;
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

    /** 不传 sort 时保持历史默认：createdTime 降序；当前仅支持 startTime。 */
    static Sort resolveListSort(String sort, String order) {
        if (sort == null || sort.isBlank()) {
            return Sort.by("createdTime").descending();
        }
        String field = sort.trim();
        if (!"startTime".equals(field)) {
            throw new IllegalArgumentException("sort 无效");
        }
        String direction = order == null || order.isBlank() ? "desc" : order.trim().toLowerCase(Locale.ROOT);
        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            throw new IllegalArgumentException("order 无效");
        }
        return "asc".equals(direction) ? Sort.by(field).ascending() : Sort.by(field).descending();
    }

    /**
     * 解析直播状态筛选：优先 {@code states}（逗号分隔多选），否则单值 {@code state}。
     * 合法取值 1～5；{@code 0} / 空表示不过滤。非法 → 400。
     */
    static List<Integer> resolveStateFilters(Integer state, String states) {
        if (states != null && !states.isBlank()) {
            LinkedHashSet<Integer> set = new LinkedHashSet<>();
            for (String part : states.split(",")) {
                String token = part.trim();
                if (token.isEmpty()) {
                    continue;
                }
                int value;
                try {
                    value = Integer.parseInt(token);
                } catch (NumberFormatException ex) {
                    throw new IllegalArgumentException("states 无效");
                }
                if (value == 0) {
                    continue;
                }
                if (value < 1 || value > 5) {
                    throw new IllegalArgumentException("states 无效");
                }
                set.add(value);
            }
            return List.copyOf(set);
        }
        if (state == null || state == 0) {
            return List.of();
        }
        if (state < 1 || state > 5) {
            throw new IllegalArgumentException("state 无效");
        }
        return List.of(state);
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

    private void attachReport(ActivityDetailResponse detail, ActivityEntity entity) {
        String reportId = trimToNull(entity.getReportId());
        if (reportId == null) {
            return;
        }
        detail.setReportId(reportId);
        liveReportRepository.findById(reportId).ifPresent(report -> detail.setReportTitle(report.getTitle()));
    }

    private LiveReportEntity requirePassedReport(String reportId) {
        String id = UuidIds.require(reportId, "reportId 无效", "reportId 无效");
        LiveReportEntity report = liveReportRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("直播报备不存在"));
        if (report.getDeletedTime() != null) {
            throw new IllegalArgumentException("直播报备不存在");
        }
        if (!"passed".equals(report.getStatus())) {
            throw new IllegalArgumentException("只能绑定已通过的直播报备");
        }
        return report;
    }

    private void assertHostMatchesReport(LiveReportEntity report, List<Long> hostIds) {
        if (hostIds.size() != 1 || !report.getHostId().equals(hostIds.get(0))) {
            throw new IllegalArgumentException("已绑定直播报备不可修改");
        }
    }

    private void bumpBoundCount(LiveReportEntity report, int delta) {
        int current = report.getBoundLiveCount() == null ? 0 : report.getBoundLiveCount();
        int next = current + delta;
        if (next < 0) {
            next = 0;
        }
        report.setBoundLiveCount(next);
        liveReportRepository.save(report);
    }

    private record ProductSnap(String spuId, String productName) {
    }

    private record CoursewareSnap(String materialId, String name, String url, String type) {
    }
}
