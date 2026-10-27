package com.zhibo.admin.activity;

import com.zhibo.activity.ActivityAudienceEntity;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.ActivityCommentEntity;
import com.zhibo.activity.ActivityCommentRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.activity.dto.AudienceCommentListItem;
import com.zhibo.admin.activity.dto.AudienceCommentListResponse;
import com.zhibo.admin.activity.dto.AudienceImportResponse;
import com.zhibo.admin.activity.dto.AudienceListItem;
import com.zhibo.admin.activity.dto.AudienceListResponse;
import com.zhibo.admin.activity.dto.CreateAudienceResponse;
import com.zhibo.admin.activity.dto.UpdateEntitlementResponse;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerTagEntity;
import com.zhibo.viewer.ViewerTagMemberEntity;
import com.zhibo.viewer.ViewerTagMemberRepository;
import com.zhibo.viewer.ViewerTagRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ActivityAudienceService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int ERRORS_CAP = 50;
    private static final Set<String> KEYWORD_TYPES = Set.of("nickname", "id", "phone");
    private static final Set<String> TAG_MATCH_MODES = Set.of("any", "all", "none");
    private static final Set<String> DURATION_OPS = Set.of("gte", "lte");
    /** 本轮占位：列表四时长恒 0（与 API约定一致）。 */
    private static final long PLACEHOLDER_DURATION_SEC = 0L;

    private final ActivityRepository activityRepository;
    private final ActivityAudienceRepository audienceRepository;
    private final ActivityCommentRepository commentRepository;
    private final ViewerRepository viewerRepository;
    private final ViewerTagMemberRepository viewerTagMemberRepository;
    private final ViewerTagRepository viewerTagRepository;
    private final SysUserAccountRepository userRepository;

    @Transactional(readOnly = true)
    public AudienceListResponse list(
            String activityId,
            int page,
            int pageSize,
            String keywordType,
            String keyword,
            List<Long> tagIds,
            String tagMatch,
            Integer entitlementStatus,
            String totalWatchDurationOp,
            Long totalWatchDurationSec,
            String liveWatchDurationOp,
            Long liveWatchDurationSec,
            String sort,
            String order) {
        String uuid = requireActivity(activityId).getId();
        int p = Math.max(page, 1);
        int size = pageSize < 1 || pageSize > 100 ? 20 : pageSize;

        String type = resolveKeywordType(keywordType);
        String kw = keyword == null ? "" : keyword.trim();
        List<Long> filterTagIds = normalizeFilterTagIds(tagIds);
        String match = resolveTagMatch(tagMatch);
        DurationFilter totalWatchFilter = resolveDurationFilter("totalWatchDuration", totalWatchDurationOp, totalWatchDurationSec);
        DurationFilter liveWatchFilter = resolveDurationFilter("liveWatchDuration", liveWatchDurationOp, liveWatchDurationSec);

        if ("id".equals(type) && !kw.isEmpty() && parsePositiveLong(kw) == null) {
            return emptyPage(p, size);
        }

        Set<Long> keywordViewerIds = resolveKeywordViewerIds(type, kw);
        if (keywordViewerIds != null && keywordViewerIds.isEmpty()) {
            return emptyPage(p, size);
        }

        final Set<Long> keywordFilter = keywordViewerIds;
        final List<Long> tagFilter = filterTagIds;
        final String tagMatchMode = match;
        Specification<ActivityAudienceEntity> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(root.get("activityId"), uuid));
            if (entitlementStatus != null) {
                preds.add(cb.equal(root.get("entitlementStatus"), entitlementStatus));
            }
            if (keywordFilter != null) {
                preds.add(root.get("viewerId").in(keywordFilter));
            }
            if (!tagFilter.isEmpty() && query != null) {
                if ("all".equals(tagMatchMode)) {
                    for (Long tagId : tagFilter) {
                        preds.add(existsMember(root, query, cb, List.of(tagId)));
                    }
                } else if ("none".equals(tagMatchMode)) {
                    preds.add(cb.not(existsMember(root, query, cb, tagFilter)));
                } else {
                    preds.add(existsMember(root, query, cb, tagFilter));
                }
            }
            return cb.and(preds.toArray(Predicate[]::new));
        };

        List<ActivityAudienceEntity> all = new ArrayList<>(audienceRepository.findAll(spec));
        // 本轮时长占位恒 0；筛选用与列表字段相同的值，后续接真实管道时改 resolve*DurationSec。
        if (totalWatchFilter != null || liveWatchFilter != null) {
            all.removeIf(row -> !matchesDurationFilters(row, totalWatchFilter, liveWatchFilter));
        }
        Map<Long, Long> commentCounts = loadCommentCounts(uuid, all.stream()
                .map(ActivityAudienceEntity::getViewerId)
                .collect(Collectors.toSet()));

        String sortKey = sort == null ? "" : sort.trim();
        boolean asc = "asc".equalsIgnoreCase(order);
        Comparator<ActivityAudienceEntity> cmp = comparator(sortKey, commentCounts);
        if (!asc) {
            cmp = cmp.reversed();
        }
        all.sort(cmp.thenComparing(ActivityAudienceEntity::getId, Comparator.reverseOrder()));

        long total = all.size();
        int from = Math.min((p - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        List<ActivityAudienceEntity> pageRows = all.subList(from, to);

        Map<Long, ViewerEntity> viewers = loadViewers(pageRows);
        Map<Long, String> employeeNames = loadEmployeeNames(pageRows);
        Map<Long, List<TagRef>> tagsByViewer = loadTagsByViewer(pageRows);

        AudienceListResponse response = new AudienceListResponse();
        response.setPage(p);
        response.setPageSize(size);
        response.setTotal(total);
        for (ActivityAudienceEntity row : pageRows) {
            response.getList()
                    .add(toListItem(
                            row,
                            viewers,
                            employeeNames,
                            commentCounts,
                            tagsByViewer.getOrDefault(row.getViewerId(), List.of())));
        }
        return response;
    }

    private static Predicate existsMember(
            Root<ActivityAudienceEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            List<Long> tagIds) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<ViewerTagMemberEntity> m = sq.from(ViewerTagMemberEntity.class);
        sq.select(m.get("id"));
        sq.where(
                cb.equal(m.get("viewerId"), root.get("viewerId")),
                m.get("tagId").in(tagIds));
        return cb.exists(sq);
    }

    private Set<Long> resolveKeywordViewerIds(String type, String kw) {
        if (kw.isEmpty()) {
            return null;
        }
        return switch (type) {
            case "id" -> {
                Long id = parsePositiveLong(kw);
                yield id == null ? Set.of() : Set.of(id);
            }
            case "phone" -> viewerRepository
                    .findAll((root, query, cb) -> cb.like(root.get("phone"), "%" + kw + "%"))
                    .stream()
                    .map(ViewerEntity::getId)
                    .collect(Collectors.toSet());
            default -> viewerRepository.findByNicknameContaining(kw).stream()
                    .map(ViewerEntity::getId)
                    .collect(Collectors.toSet());
        };
    }

    private static AudienceListResponse emptyPage(int page, int pageSize) {
        AudienceListResponse empty = new AudienceListResponse();
        empty.setPage(page);
        empty.setPageSize(pageSize);
        empty.setTotal(0);
        return empty;
    }

    private static String resolveKeywordType(String keywordType) {
        if (keywordType == null || keywordType.isBlank()) {
            return "nickname";
        }
        String t = keywordType.trim().toLowerCase(Locale.ROOT);
        if (!KEYWORD_TYPES.contains(t)) {
            throw new IllegalArgumentException("keywordType 仅支持 nickname / id / phone");
        }
        return t;
    }

    private static String resolveTagMatch(String tagMatch) {
        if (tagMatch == null || tagMatch.isBlank()) {
            return "any";
        }
        String m = tagMatch.trim().toLowerCase(Locale.ROOT);
        if (!TAG_MATCH_MODES.contains(m)) {
            throw new IllegalArgumentException("tagMatch 仅支持 any / all / none");
        }
        return m;
    }

    /**
     * 时长筛选：仅当 sec 有值时生效；op 缺省 gte。sec 须非负。
     *
     * @param fieldLabel 用于错误文案，如 totalWatchDuration
     */
    static DurationFilter resolveDurationFilter(String fieldLabel, String op, Long sec) {
        if (sec == null) {
            return null;
        }
        if (sec < 0) {
            throw new IllegalArgumentException(fieldLabel + "Sec 须为非负整数");
        }
        String resolved = resolveDurationOp(fieldLabel, op);
        return new DurationFilter(resolved, sec);
    }

    private static String resolveDurationOp(String fieldLabel, String op) {
        if (op == null || op.isBlank()) {
            return "gte";
        }
        String o = op.trim().toLowerCase(Locale.ROOT);
        if (!DURATION_OPS.contains(o)) {
            throw new IllegalArgumentException(fieldLabel + "Op 仅支持 gte / lte");
        }
        return o;
    }

    private static boolean matchesDurationFilters(
            ActivityAudienceEntity row, DurationFilter totalWatch, DurationFilter liveWatch) {
        if (totalWatch != null && !matchesDuration(resolveTotalWatchDurationSec(row), totalWatch)) {
            return false;
        }
        if (liveWatch != null && !matchesDuration(resolveLiveWatchDurationSec(row), liveWatch)) {
            return false;
        }
        return true;
    }

    private static boolean matchesDuration(long actualSec, DurationFilter filter) {
        return "lte".equals(filter.op()) ? actualSec <= filter.sec() : actualSec >= filter.sec();
    }

    /** 本轮占位恒 0；接真实管道时从 row / 聚合表读取。 */
    private static long resolveTotalWatchDurationSec(ActivityAudienceEntity row) {
        return PLACEHOLDER_DURATION_SEC;
    }

    private static long resolveLiveWatchDurationSec(ActivityAudienceEntity row) {
        return PLACEHOLDER_DURATION_SEC;
    }

    record DurationFilter(String op, long sec) {}

    static List<Long> normalizeFilterTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<Long, Boolean> uniq = new LinkedHashMap<>();
        for (Long id : tagIds) {
            if (id != null && id > 0) {
                uniq.put(id, Boolean.TRUE);
            }
        }
        return new ArrayList<>(uniq.keySet());
    }

    private static Long parsePositiveLong(String raw) {
        try {
            long id = Long.parseLong(raw.trim());
            return id > 0 ? id : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Transactional
    public CreateAudienceResponse create(String activityId, String phone, long operatorId) {
        String uuid = requireActivity(activityId).getId();
        String normalized = normalizePhone(phone);
        ViewerEntity viewer = viewerRepository
                .findByPhone(normalized)
                .orElseThrow(() -> new IllegalArgumentException("未找到该手机号观众"));
        requireActiveEmployee(operatorId);

        Optional<ActivityAudienceEntity> existing =
                audienceRepository.findByActivityIdAndViewerId(uuid, viewer.getId());
        LocalDateTime now = LocalDateTime.now();
        if (existing.isPresent()) {
            ActivityAudienceEntity row = existing.get();
            if (row.getImporterId() == null) {
                row.setImporterId(operatorId);
                row.setImportTime(now);
                audienceRepository.save(row);
            }
            return new CreateAudienceResponse(row.getId(), row.getViewerId());
        }

        ActivityAudienceEntity row = new ActivityAudienceEntity();
        row.setActivityId(uuid);
        row.setViewerId(viewer.getId());
        row.setEntitlementStatus(1);
        row.setImporterId(operatorId);
        row.setImportTime(now);
        audienceRepository.save(row);
        return new CreateAudienceResponse(row.getId(), row.getViewerId());
    }

    @Transactional
    public AudienceImportResponse importCsv(String activityId, MultipartFile file, long operatorId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            throw new IllegalArgumentException("请使用 CSV 模板导入（Excel 请另存为 CSV）");
        }
        String uuid = requireActivity(activityId).getId();
        requireActiveEmployee(operatorId);

        List<PhoneRow> phones = parsePhoneCsv(file);
        AudienceImportResponse result = new AudienceImportResponse();
        result.setTotal(phones.size());
        if (phones.isEmpty()) {
            return result;
        }

        Set<String> uniquePhones = phones.stream().map(PhoneRow::phone).collect(Collectors.toSet());
        Map<String, ViewerEntity> byPhone = viewerRepository.findByPhoneIn(uniquePhones).stream()
                .filter(v -> v.getPhone() != null)
                .collect(Collectors.toMap(ViewerEntity::getPhone, v -> v, (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();
        for (PhoneRow pr : phones) {
            ViewerEntity viewer = byPhone.get(pr.phone());
            if (viewer == null) {
                result.setFailed(result.getFailed() + 1);
                addImportError(result, pr.row(), pr.phone(), "未找到该手机号观众");
                continue;
            }
            Optional<ActivityAudienceEntity> existing =
                    audienceRepository.findByActivityIdAndViewerId(uuid, viewer.getId());
            if (existing.isPresent()) {
                ActivityAudienceEntity row = existing.get();
                if (row.getImporterId() == null) {
                    row.setImporterId(operatorId);
                    row.setImportTime(now);
                    audienceRepository.save(row);
                    result.setUpdated(result.getUpdated() + 1);
                } else {
                    result.setSkipped(result.getSkipped() + 1);
                }
                continue;
            }
            ActivityAudienceEntity row = new ActivityAudienceEntity();
            row.setActivityId(uuid);
            row.setViewerId(viewer.getId());
            row.setEntitlementStatus(1);
            row.setImporterId(operatorId);
            row.setImportTime(now);
            audienceRepository.save(row);
            result.setCreated(result.getCreated() + 1);
        }
        return result;
    }

    public byte[] importTemplateCsv() {
        return "手机号\n".getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public UpdateEntitlementResponse updateEntitlement(long rowId, int entitlementStatus) {
        if (entitlementStatus != 0 && entitlementStatus != 1) {
            throw new IllegalArgumentException("entitlementStatus 须为 0 或 1");
        }
        ActivityAudienceEntity row = audienceRepository
                .findById(rowId)
                .orElseThrow(() -> new IllegalArgumentException("观众行不存在"));
        row.setEntitlementStatus(entitlementStatus);
        audienceRepository.save(row);
        return new UpdateEntitlementResponse(row.getId(), row.getEntitlementStatus());
    }

    @Transactional(readOnly = true)
    public AudienceCommentListResponse listComments(String activityId, int page, int pageSize, String nickname) {
        String uuid = requireActivity(activityId).getId();
        int p = Math.max(page, 1);
        int size = pageSize < 1 || pageSize > 100 ? 20 : pageSize;
        PageRequest pr = PageRequest.of(p - 1, size);
        Page<ActivityCommentEntity> result;
        if (nickname != null && !nickname.isBlank()) {
            result = commentRepository.findByActivityIdAndNicknameContainingOrderByDeletedTimeAscCommentTimeDesc(
                    uuid, nickname.trim(), pr);
        } else {
            result = commentRepository.findByActivityIdOrderByDeletedTimeAscCommentTimeDesc(uuid, pr);
        }
        AudienceCommentListResponse response = new AudienceCommentListResponse();
        response.setPage(p);
        response.setPageSize(size);
        response.setTotal(result.getTotalElements());
        for (ActivityCommentEntity c : result.getContent()) {
            AudienceCommentListItem item = new AudienceCommentListItem();
            item.setId(c.getId());
            item.setViewerId(c.getViewerId());
            item.setGuestId(c.getGuestId());
            item.setNickname(c.getNickname());
            item.setContent(c.getContent());
            item.setCommentTime(formatTime(c.getCommentTime()));
            item.setDeleted(c.getDeletedTime() != null);
            response.getList().add(item);
        }
        return response;
    }

    private AudienceListItem toListItem(
            ActivityAudienceEntity row,
            Map<Long, ViewerEntity> viewers,
            Map<Long, String> employeeNames,
            Map<Long, Long> commentCounts,
            List<TagRef> tags) {
        AudienceListItem item = new AudienceListItem();
        item.setId(row.getId());
        item.setViewerId(row.getViewerId());
        ViewerEntity viewer = viewers.get(row.getViewerId());
        String nick = viewer != null && viewer.getNickname() != null && !viewer.getNickname().isBlank()
                ? viewer.getNickname().trim()
                : "用户" + row.getViewerId();
        item.setNickname(nick);
        if (viewer != null && viewer.getAvatarUrl() != null && !viewer.getAvatarUrl().isBlank()) {
            item.setAvatarUrl(viewer.getAvatarUrl());
        }
        item.setEntitlementStatus(row.getEntitlementStatus());
        item.setEmployeeInviterId(row.getEmployeeInviterId());
        item.setEmployeeInviteTime(formatTime(row.getEmployeeInviteTime()));
        item.setImporterId(row.getImporterId());
        item.setImportTime(formatTime(row.getImportTime()));
        if (row.getImporterId() != null) {
            item.setImporterName(employeeNames.get(row.getImporterId()));
        }

        // 所属邀请人只展示员工；无员工邀请则栏空（不展示观众邀请）
        if (row.getEmployeeInviterId() != null) {
            item.setInviterKind("employee");
            item.setInviterDisplayName(
                    employeeNames.getOrDefault(row.getEmployeeInviterId(), "员工" + row.getEmployeeInviterId()));
            item.setInviteTime(formatTime(row.getEmployeeInviteTime()));
        }

        List<Long> tagIdList = new ArrayList<>();
        List<String> tagLabelList = new ArrayList<>();
        for (TagRef ref : tags) {
            tagIdList.add(ref.tagId());
            tagLabelList.add(ref.name());
        }
        item.setTagIds(tagIdList);
        item.setTagLabels(tagLabelList);

        item.setStayDurationSec(PLACEHOLDER_DURATION_SEC);
        item.setTotalWatchDurationSec(resolveTotalWatchDurationSec(row));
        item.setLiveWatchDurationSec(resolveLiveWatchDurationSec(row));
        item.setReplayWatchDurationSec(PLACEHOLDER_DURATION_SEC);
        item.setCommentCount(commentCounts.getOrDefault(row.getViewerId(), 0L));
        item.setDealAmount("0.00");
        return item;
    }

    private Map<Long, List<TagRef>> loadTagsByViewer(List<ActivityAudienceEntity> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> viewerIds = rows.stream().map(ActivityAudienceEntity::getViewerId).distinct().toList();
        List<ViewerTagMemberEntity> members = viewerTagMemberRepository.findByViewerIdIn(viewerIds);
        if (members.isEmpty()) {
            return Map.of();
        }
        Set<Long> tagIds = new HashSet<>();
        for (ViewerTagMemberEntity m : members) {
            tagIds.add(m.getTagId());
        }
        Map<Long, ViewerTagEntity> tags = loadActiveTags(tagIds);
        Map<Long, List<TagRef>> grouped = new HashMap<>();
        for (ViewerTagMemberEntity m : members) {
            ViewerTagEntity tag = tags.get(m.getTagId());
            if (tag == null) {
                continue;
            }
            grouped.computeIfAbsent(m.getViewerId(), k -> new ArrayList<>())
                    .add(new TagRef(tag.getId(), tag.getName()));
        }
        return grouped;
    }

    private Map<Long, ViewerTagEntity> loadActiveTags(Set<Long> tagIds) {
        if (tagIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ViewerTagEntity> map = new LinkedHashMap<>();
        for (ViewerTagEntity tag : viewerTagRepository.findAllById(tagIds)) {
            if (tag.getDeletedTime() == null) {
                map.put(tag.getId(), tag);
            }
        }
        return map;
    }

    private record TagRef(long tagId, String name) {}

    private Map<Long, Long> loadCommentCounts(String activityId, Set<Long> viewerIds) {
        Map<Long, Long> map = new HashMap<>();
        if (viewerIds == null || viewerIds.isEmpty()) {
            return map;
        }
        for (Object[] row : commentRepository.countActiveByViewerIds(activityId, viewerIds)) {
            if (row[0] != null) {
                map.put((Long) row[0], (Long) row[1]);
            }
        }
        return map;
    }

    private Map<Long, ViewerEntity> loadViewers(List<ActivityAudienceEntity> rows) {
        Set<Long> ids = new HashSet<>();
        for (ActivityAudienceEntity row : rows) {
            ids.add(row.getViewerId());
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return viewerRepository.findByIdIn(ids).stream()
                .collect(Collectors.toMap(ViewerEntity::getId, v -> v, (a, b) -> a));
    }

    private Map<Long, String> loadEmployeeNames(List<ActivityAudienceEntity> rows) {
        Set<Long> ids = new HashSet<>();
        for (ActivityAudienceEntity row : rows) {
            if (row.getEmployeeInviterId() != null) {
                ids.add(row.getEmployeeInviterId());
            }
            if (row.getImporterId() != null) {
                ids.add(row.getImporterId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> map = new HashMap<>();
        for (SysUserAccountEntity u : userRepository.findAllById(ids)) {
            map.put(u.getId(), u.getUserName());
        }
        return map;
    }

    private static Comparator<ActivityAudienceEntity> comparator(
            String sortKey, Map<Long, Long> commentCounts) {
        return switch (sortKey) {
            case "commentCount" -> Comparator.comparingLong(
                    (ActivityAudienceEntity r) -> commentCounts.getOrDefault(r.getViewerId(), 0L));
            case "stayDuration", "totalWatchDuration", "liveWatchDuration", "replayWatchDuration", "dealAmount" ->
                    Comparator.comparingLong(ActivityAudienceEntity::getId);
            default -> Comparator.comparing(
                    ActivityAudienceEntity::getCreatedTime, Comparator.nullsLast(Comparator.naturalOrder()));
        };
    }

    private ActivityEntity requireActivity(String activityId) {
        String uuid = ActivityIds.requireUuid(activityId);
        return activityRepository
                .findById(uuid)
                .filter(a -> a.getDeletedTime() == null)
                .orElseThrow(() -> new IllegalArgumentException("活动不存在"));
    }

    private void requireActiveEmployee(long userId) {
        SysUserAccountEntity user = userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("操作人无效"));
        if (user.getRsStatus() == null || user.getRsStatus() != 1) {
            throw new IllegalArgumentException("操作人无效");
        }
    }

    private static String normalizePhone(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        String digits = phone.trim().replaceAll("\\D", "");
        if (digits.length() < 5 || digits.length() > 20) {
            throw new IllegalArgumentException("手机号格式无效");
        }
        return digits;
    }

    private static List<PhoneRow> parsePhoneCsv(MultipartFile file) {
        List<PhoneRow> rows = new ArrayList<>();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            int rowNum = 0;
            boolean headerSkipped = false;
            while ((line = reader.readLine()) != null) {
                rowNum++;
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (trimmed.charAt(0) == '\uFEFF') {
                    trimmed = trimmed.substring(1);
                }
                String firstCell = trimmed.split("[,，\\t]", 2)[0].trim();
                if (!headerSkipped
                        && (firstCell.contains("手机") || firstCell.equalsIgnoreCase("phone"))) {
                    headerSkipped = true;
                    continue;
                }
                headerSkipped = true;
                String digits = firstCell.replaceAll("\\D", "");
                if (digits.isEmpty()) {
                    continue;
                }
                rows.add(new PhoneRow(rowNum, digits));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("无法解析导入文件");
        }
        return rows;
    }

    private static void addImportError(AudienceImportResponse result, int row, String phone, String reason) {
        if (result.getErrors().size() >= ERRORS_CAP) {
            return;
        }
        AudienceImportResponse.ErrorItem err = new AudienceImportResponse.ErrorItem();
        err.setRow(row);
        err.setPhone(phone);
        err.setReason(reason);
        result.getErrors().add(err);
    }

    private static String formatTime(LocalDateTime time) {
        return time == null ? null : TIME_FMT.format(time);
    }

    private record PhoneRow(int row, String phone) {}
}
