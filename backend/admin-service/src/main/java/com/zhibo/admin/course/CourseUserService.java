package com.zhibo.admin.course;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.course.dto.CourseUserEntitlementResponse;
import com.zhibo.admin.course.dto.CourseUserExpireRequest;
import com.zhibo.admin.course.dto.CourseUserExpireResponse;
import com.zhibo.admin.course.dto.CourseUserImportHistoryItem;
import com.zhibo.admin.course.dto.CourseUserImportHistoryListResponse;
import com.zhibo.admin.course.dto.CourseUserImportResponse;
import com.zhibo.admin.course.dto.CourseUserListItem;
import com.zhibo.admin.course.dto.CourseUserListResponse;
import com.zhibo.admin.course.dto.CreateCourseUserRequest;
import com.zhibo.admin.course.dto.CreateCourseUserResponse;
import com.zhibo.admin.course.dto.ImportCourseUsersByTagsEstimateResponse;
import com.zhibo.admin.course.dto.ImportCourseUsersByTagsRequest;
import com.zhibo.admin.course.dto.ImportCourseUsersByTagsResponse;
import com.zhibo.admin.viewer.ViewerAdminService;
import com.zhibo.common.UuidIds;
import com.zhibo.course.CourseCatalogNodeType;
import com.zhibo.course.CourseCatalogNodeRepository;
import com.zhibo.course.CourseEntitlementSupport;
import com.zhibo.course.CourseEntity;
import com.zhibo.course.CourseLessonParticipateStatus;
import com.zhibo.course.CourseLessonProgressRepository;
import com.zhibo.course.CourseRepository;
import com.zhibo.course.CourseUserEntity;
import com.zhibo.course.CourseUserImportEntity;
import com.zhibo.course.CourseUserImportRepository;
import com.zhibo.course.CourseUserRepository;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerTagEntity;
import com.zhibo.viewer.ViewerTagMemberEntity;
import com.zhibo.viewer.ViewerTagMemberRepository;
import com.zhibo.viewer.ViewerTagRepository;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** 课程用户列表、添加/导入与权益开关（D100）。 */
@Service
@RequiredArgsConstructor
public class CourseUserService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Set<String> STATE_FILTERS = Set.of("valid", "expired", "invalid");
    private static final Set<String> KEYWORD_TYPES = Set.of("nickname", "id", "phone");
    private static final int ERRORS_CAP = 50;
    private static final int TAG_IMPORT_MAX = 5;
    private static final byte[] UTF8_BOM = new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final TypeReference<List<CourseUserImportResponse.ErrorItem>> FAIL_DETAILS_TYPE =
            new TypeReference<>() {};

    private final CourseRepository courseRepository;
    private final CourseUserRepository courseUserRepository;
    private final CourseUserImportRepository courseUserImportRepository;
    private final CourseCatalogNodeRepository catalogNodeRepository;
    private final CourseLessonProgressRepository progressRepository;
    private final ViewerRepository viewerRepository;
    private final ViewerTagMemberRepository viewerTagMemberRepository;
    private final ViewerTagRepository viewerTagRepository;
    private final SysUserAccountRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public CourseUserListResponse list(
            String courseId,
            int page,
            int pageSize,
            String keywordType,
            String keyword,
            String entitlementState) {
        PreparedList prepared = prepareList(courseId, keywordType, keyword, entitlementState);
        int p = Math.max(page, 1);
        int size = pageSize < 1 || pageSize > 100 ? 20 : pageSize;
        List<CourseUserEntity> filtered = prepared.rows();
        long total = filtered.size();
        int from = Math.min((p - 1) * size, filtered.size());
        int to = Math.min(from + size, filtered.size());
        List<CourseUserEntity> pageRows = filtered.subList(from, to);

        Map<Long, ViewerEntity> viewers = loadViewers(pageRows);
        Map<Long, List<TagRef>> tagsByViewer = loadTagsByViewer(pageRows);
        long totalLessons = catalogNodeRepository.countActiveByCourseIdAndNodeType(
                prepared.courseId(), CourseCatalogNodeType.LESSON);
        Map<Long, Long> completedByViewer = loadCompletedLessonCounts(prepared.courseId(), pageRows);
        CourseUserListResponse response = new CourseUserListResponse();
        response.setPage(p);
        response.setPageSize(size);
        response.setTotal(total);
        for (CourseUserEntity row : pageRows) {
            long completed = completedByViewer.getOrDefault(row.getViewerId(), 0L);
            response.getList().add(toItem(
                    row,
                    viewers.get(row.getViewerId()),
                    tagsByViewer.getOrDefault(row.getViewerId(), List.of()),
                    prepared.accessType(),
                    prepared.now(),
                    completionRate(completed, totalLessons)));
        }
        return response;
    }

    /** 按当前筛选导出本课学员 CSV（不分页）。 */
    @Transactional(readOnly = true)
    public byte[] exportCsv(
            String courseId, String keywordType, String keyword, String entitlementState) {
        PreparedList prepared = prepareList(courseId, keywordType, keyword, entitlementState);
        List<CourseUserEntity> rows = prepared.rows();
        Map<Long, ViewerEntity> viewers = loadViewers(rows);
        Map<Long, List<TagRef>> tagsByViewer = loadTagsByViewer(rows);
        long totalLessons = catalogNodeRepository.countActiveByCourseIdAndNodeType(
                prepared.courseId(), CourseCatalogNodeType.LESSON);
        Map<Long, Long> completedByViewer = loadCompletedLessonCounts(prepared.courseId(), rows);

        StringBuilder sb = new StringBuilder();
        sb.append("学员,用户ID,手机号,标签,小节完成率,权益状态,权益获取方式,权益首次获取时间,权益到期时间\n");
        for (CourseUserEntity row : rows) {
            long completed = completedByViewer.getOrDefault(row.getViewerId(), 0L);
            CourseUserListItem item = toItem(
                    row,
                    viewers.get(row.getViewerId()),
                    tagsByViewer.getOrDefault(row.getViewerId(), List.of()),
                    prepared.accessType(),
                    prepared.now(),
                    completionRate(completed, totalLessons));
            ViewerEntity viewer = viewers.get(row.getViewerId());
            String phone = viewer != null && viewer.getPhone() != null ? viewer.getPhone() : "";
            sb.append(csvCell(item.getNickname()))
                    .append(',')
                    .append(row.getViewerId() == null ? "" : row.getViewerId())
                    .append(',')
                    .append(csvCell(phone))
                    .append(',')
                    .append(csvCell(item.getTagLabels() == null ? "" : String.join("、", item.getTagLabels())))
                    .append(',')
                    .append(String.format(Locale.ROOT, "%.1f%%", item.getChapterCompletionRate()))
                    .append(',')
                    .append(csvCell(item.getEntitlementStateLabel()))
                    .append(',')
                    .append(csvCell(item.getAcquireWayLabel()))
                    .append(',')
                    .append(csvCell(item.getFirstAcquireTime()))
                    .append(',')
                    .append(csvCell(item.getExpireDisplay()))
                    .append('\n');
        }
        return csvBytes(sb.toString());
    }

    private PreparedList prepareList(
            String courseId, String keywordType, String keyword, String entitlementState) {
        CourseEntity course = requireCourse(courseId);
        String stateFilter = normalizeStateFilter(entitlementState);
        String type = resolveKeywordType(keywordType);
        String kw = keyword == null ? "" : keyword.trim();
        String accessType = course.getAccessValidityType() == null ? "long" : course.getAccessValidityType();
        LocalDateTime now = LocalDateTime.now();

        List<CourseUserEntity> rows;
        if (!kw.isEmpty()) {
            Set<Long> viewerIds = resolveKeywordViewerIds(type, kw);
            if (viewerIds.isEmpty()) {
                return new PreparedList(List.of(), accessType, now, course.getId());
            }
            rows = courseUserRepository.findByCourseIdAndViewerIdInOrderByCreatedTimeDescIdDesc(
                    course.getId(), viewerIds);
        } else {
            rows = courseUserRepository.findByCourseIdOrderByCreatedTimeDescIdDesc(course.getId());
        }

        List<CourseUserEntity> filtered = new ArrayList<>();
        for (CourseUserEntity row : rows) {
            String state = CourseEntitlementSupport.resolveState(
                    row.getEntitlementStatus(),
                    accessType,
                    row.getEntitlementExpireTime(),
                    now);
            if (stateFilter != null && !stateFilter.equals(state)) {
                continue;
            }
            filtered.add(row);
        }
        return new PreparedList(filtered, accessType, now, course.getId());
    }

    @Transactional
    public CourseUserEntitlementResponse updateEntitlement(long rowId, Integer entitlementStatus) {
        if (entitlementStatus == null || (entitlementStatus != 0 && entitlementStatus != 1)) {
            throw new IllegalArgumentException("entitlementStatus 须为 0 或 1");
        }
        CourseUserEntity row = courseUserRepository
                .findById(rowId)
                .orElseThrow(() -> new IllegalArgumentException("学员行不存在"));
        requireCourse(row.getCourseId());
        row.setEntitlementStatus(entitlementStatus);
        courseUserRepository.save(row);
        return new CourseUserEntitlementResponse(row.getId(), row.getEntitlementStatus());
    }

    /** 延迟用户时间：手工改该行权益到期时刻。 */
    @Transactional
    public CourseUserExpireResponse updateExpire(long rowId, CourseUserExpireRequest request) {
        if (request == null || request.getExpireTime() == null || request.getExpireTime().isBlank()) {
            throw new IllegalArgumentException("请选择到期时间");
        }
        LocalDateTime expire;
        try {
            expire = LocalDateTime.parse(request.getExpireTime().trim(), TIME);
        } catch (Exception e) {
            throw new IllegalArgumentException("到期时间格式须为 yyyy-MM-dd HH:mm:ss");
        }
        CourseUserEntity row = courseUserRepository
                .findById(rowId)
                .orElseThrow(() -> new IllegalArgumentException("学员行不存在"));
        requireCourse(row.getCourseId());
        row.setEntitlementExpireTime(expire);
        courseUserRepository.save(row);
        return new CourseUserExpireResponse(row.getId(), TIME.format(expire));
    }

    @Transactional
    public CreateCourseUserResponse create(
            String courseId, CreateCourseUserRequest request, long operatorId) {
        if (request == null) {
            throw new IllegalArgumentException("请求不能为空");
        }
        boolean hasPhone = request.getPhone() != null && !request.getPhone().isBlank();
        boolean hasViewerId = request.getViewerId() != null;
        if (hasPhone == hasViewerId) {
            if (hasPhone) {
                throw new IllegalArgumentException("手机号与用户ID不能同时传");
            }
            throw new IllegalArgumentException("请填写手机号或用户ID");
        }

        ViewerEntity viewer;
        if (hasPhone) {
            String normalized = normalizePhone(request.getPhone());
            viewer = viewerRepository
                    .findByPhone(normalized)
                    .orElseThrow(() -> new IllegalArgumentException("未找到该手机号观众"));
        } else {
            long viewerId = request.getViewerId();
            if (viewerId <= 0) {
                throw new IllegalArgumentException("用户ID无效");
            }
            viewer = viewerRepository
                    .findById(viewerId)
                    .orElseThrow(() -> new IllegalArgumentException("未找到该用户"));
        }

        CourseEntity course = requireCourse(courseId);
        requireActiveEmployee(operatorId);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime customExpire = parseOptionalExpireTime(request.getExpireTime());

        Optional<CourseUserEntity> existing =
                courseUserRepository.findByCourseIdAndViewerId(course.getId(), viewer.getId());
        if (existing.isPresent()) {
            CourseUserEntity row = existing.get();
            if (row.getImporterId() == null) {
                row.setImporterId(operatorId);
                // 导入时间口径 = created_time（入表时间），不另起时刻
                row.setImportTime(row.getCreatedTime() != null ? row.getCreatedTime() : now);
                courseUserRepository.save(row);
            }
            // 已有行不改 expire
            return new CreateCourseUserResponse(row.getId(), row.getViewerId(), false);
        }

        CourseUserEntity row = new CourseUserEntity();
        row.setCourseId(course.getId());
        row.setViewerId(viewer.getId());
        row.setEntitlementStatus(1);
        row.setAcquireWay("import");
        row.setImporterId(operatorId);
        // 与 created_time 同刻写入；展示统一用 created_time
        row.setImportTime(now);
        row.setEntitlementExpireTime(resolveJoinExpire(course, now, customExpire));
        courseUserRepository.save(row);

        course.setTotalUserCount(Math.max(0, course.getTotalUserCount()) + 1);
        courseRepository.save(course);

        return new CreateCourseUserResponse(row.getId(), row.getViewerId(), true);
    }

    @Transactional
    public CourseUserImportResponse importCsv(
            String courseId, MultipartFile file, String expireTime, long operatorId) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请上传文件");
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            throw new IllegalArgumentException("请使用 CSV 模板导入（Excel 请另存为 CSV）");
        }
        CourseEntity course = requireCourse(courseId);
        requireActiveEmployee(operatorId);
        LocalDateTime customExpire = parseOptionalExpireTime(expireTime);

        List<PhoneRow> phones = parsePhoneCsv(file);
        CourseUserImportResponse result = new CourseUserImportResponse();
        result.setTotal(phones.size());
        List<CourseUserImportResponse.ErrorItem> allFailures = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int createdCount = 0;

        if (!phones.isEmpty()) {
            Set<String> uniquePhones = phones.stream().map(PhoneRow::phone).collect(Collectors.toSet());
            Map<String, ViewerEntity> byPhone = viewerRepository.findByPhoneIn(uniquePhones).stream()
                    .filter(v -> v.getPhone() != null)
                    .collect(Collectors.toMap(ViewerEntity::getPhone, v -> v, (a, b) -> a));

            for (PhoneRow pr : phones) {
                ViewerEntity viewer = byPhone.get(pr.phone());
                if (viewer == null) {
                    result.setFailed(result.getFailed() + 1);
                    addImportError(result, allFailures, pr.row(), pr.phone(), "未找到该手机号观众");
                    continue;
                }
                Optional<CourseUserEntity> existing =
                        courseUserRepository.findByCourseIdAndViewerId(course.getId(), viewer.getId());
                if (existing.isPresent()) {
                    CourseUserEntity row = existing.get();
                    if (row.getImporterId() == null) {
                        row.setImporterId(operatorId);
                        row.setImportTime(row.getCreatedTime() != null ? row.getCreatedTime() : now);
                        courseUserRepository.save(row);
                        result.setUpdated(result.getUpdated() + 1);
                    } else {
                        result.setSkipped(result.getSkipped() + 1);
                    }
                    // 已有行不改 expire
                    continue;
                }
                CourseUserEntity row = new CourseUserEntity();
                row.setCourseId(course.getId());
                row.setViewerId(viewer.getId());
                row.setEntitlementStatus(1);
                row.setAcquireWay("import");
                row.setImporterId(operatorId);
                row.setImportTime(now);
                row.setEntitlementExpireTime(resolveJoinExpire(course, now, customExpire));
                courseUserRepository.save(row);
                result.setCreated(result.getCreated() + 1);
                createdCount++;
            }
        }

        if (createdCount > 0) {
            course.setTotalUserCount(Math.max(0, course.getTotalUserCount()) + createdCount);
            courseRepository.save(course);
        }

        persistImportHistory(course.getId(), operatorId, now, result, allFailures);
        return result;
    }

    /**
     * 标签导入预计人数：所选标签「满足其一」去重后的观众数（不做写入）。
     */
    @Transactional(readOnly = true)
    public ImportCourseUsersByTagsEstimateResponse estimateByTags(
            String courseId, ImportCourseUsersByTagsRequest request) {
        List<Long> tagIds = normalizeImportTagIds(request);
        requireCourse(courseId);
        requireTagsExist(tagIds);
        long total = viewerTagMemberRepository.countDistinctViewersByTagIdIn(tagIds);
        return new ImportCourseUsersByTagsEstimateResponse((int) Math.min(total, Integer.MAX_VALUE));
    }

    /**
     * 标签导入：所选标签「满足其一」的观众 upsert 本课学员。
     * 不写 CSV 导入历史；已有行不改权益。
     */
    @Transactional
    public ImportCourseUsersByTagsResponse importByTags(
            String courseId, ImportCourseUsersByTagsRequest request, long operatorId) {
        List<Long> tagIds = normalizeImportTagIds(request);
        CourseEntity course = requireCourse(courseId);
        requireActiveEmployee(operatorId);
        requireTagsExist(tagIds);
        LocalDateTime customExpire =
                parseOptionalExpireTime(request == null ? null : request.getExpireTime());

        List<ViewerTagMemberEntity> members = viewerTagMemberRepository.findByTagIdIn(tagIds);
        LinkedHashMap<Long, Boolean> viewerUniq = new LinkedHashMap<>();
        for (ViewerTagMemberEntity m : members) {
            if (m.getViewerId() != null && m.getViewerId() > 0) {
                viewerUniq.putIfAbsent(m.getViewerId(), Boolean.TRUE);
            }
        }
        List<Long> viewerIds = List.copyOf(viewerUniq.keySet());

        ImportCourseUsersByTagsResponse result = new ImportCourseUsersByTagsResponse();
        result.setTotal(viewerIds.size());
        if (viewerIds.isEmpty()) {
            return result;
        }

        LocalDateTime now = LocalDateTime.now();
        int createdCount = 0;
        for (Long viewerId : viewerIds) {
            Optional<CourseUserEntity> existing =
                    courseUserRepository.findByCourseIdAndViewerId(course.getId(), viewerId);
            if (existing.isPresent()) {
                CourseUserEntity row = existing.get();
                if (row.getImporterId() == null) {
                    row.setImporterId(operatorId);
                    row.setImportTime(row.getCreatedTime() != null ? row.getCreatedTime() : now);
                    courseUserRepository.save(row);
                    result.setUpdated(result.getUpdated() + 1);
                } else {
                    result.setSkipped(result.getSkipped() + 1);
                }
                // 已有行不改 expire
                continue;
            }
            CourseUserEntity row = new CourseUserEntity();
            row.setCourseId(course.getId());
            row.setViewerId(viewerId);
            row.setEntitlementStatus(1);
            row.setAcquireWay("import");
            row.setImporterId(operatorId);
            row.setImportTime(now);
            row.setEntitlementExpireTime(resolveJoinExpire(course, now, customExpire));
            courseUserRepository.save(row);
            result.setCreated(result.getCreated() + 1);
            createdCount++;
        }
        if (createdCount > 0) {
            course.setTotalUserCount(Math.max(0, course.getTotalUserCount()) + createdCount);
            courseRepository.save(course);
        }
        return result;
    }

    private static List<Long> normalizeImportTagIds(ImportCourseUsersByTagsRequest request) {
        List<Long> raw = request == null || request.getTagIds() == null ? List.of() : request.getTagIds();
        LinkedHashMap<Long, Boolean> uniq = new LinkedHashMap<>();
        for (Long id : raw) {
            if (id != null && id > 0) {
                uniq.putIfAbsent(id, Boolean.TRUE);
            }
        }
        List<Long> tagIds = List.copyOf(uniq.keySet());
        if (tagIds.isEmpty()) {
            throw new IllegalArgumentException("请选择标签");
        }
        if (tagIds.size() > TAG_IMPORT_MAX) {
            throw new IllegalArgumentException("最多选择 5 个标签");
        }
        return tagIds;
    }

    private void requireTagsExist(List<Long> tagIds) {
        for (Long tagId : tagIds) {
            viewerTagRepository
                    .findByIdAndDeletedTimeIsNull(tagId)
                    .orElseThrow(() -> new IllegalArgumentException("标签不存在或已删除"));
        }
    }

    /**
     * 选填自定义到期：空 → null；{@code yyyy-MM-dd} → 当日 00:00:00；
     * {@code yyyy-MM-dd HH:mm:ss} → 原样解析。非法 → 400。
     */
    static LocalDateTime parseOptionalExpireTime(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String s = raw.trim();
        try {
            if (s.length() == 10) {
                return LocalDate.parse(s, DATE).atStartOfDay();
            }
            return LocalDateTime.parse(s, TIME);
        } catch (Exception e) {
            throw new IllegalArgumentException("到期时间格式须为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss");
        }
    }

    /** 新建学员行到期：有自定义用自定义，否则按课程售卖 {@link CourseEntitlementSupport#expireAtJoin}。 */
    static LocalDateTime resolveJoinExpire(
            CourseEntity course, LocalDateTime now, LocalDateTime customExpire) {
        if (customExpire != null) {
            return customExpire;
        }
        return CourseEntitlementSupport.expireAtJoin(course, now);
    }

    public byte[] importTemplateCsv() {
        return csvBytes("手机号\n");
    }

    @Transactional(readOnly = true)
    public CourseUserImportHistoryListResponse listImportHistory(String courseId) {
        String uuid = requireCourse(courseId).getId();
        List<CourseUserImportEntity> rows =
                courseUserImportRepository.findTop100ByCourseIdOrderByImportTimeDesc(uuid);
        Set<Long> importerIds = new HashSet<>();
        for (CourseUserImportEntity row : rows) {
            importerIds.add(row.getImporterId());
        }
        Map<Long, String> names = new HashMap<>();
        if (!importerIds.isEmpty()) {
            for (SysUserAccountEntity u : userRepository.findAllById(importerIds)) {
                names.put(u.getId(), u.getUserName());
            }
        }
        CourseUserImportHistoryListResponse resp = new CourseUserImportHistoryListResponse();
        for (CourseUserImportEntity row : rows) {
            CourseUserImportHistoryItem item = new CourseUserImportHistoryItem();
            item.setId(row.getId());
            item.setImportTime(formatTime(row.getImportTime()));
            item.setImporterId(row.getImporterId());
            item.setImporterName(names.getOrDefault(row.getImporterId(), "员工" + row.getImporterId()));
            item.setSuccessCount(row.getSuccessCount());
            item.setFailCount(row.getFailCount());
            resp.getList().add(item);
        }
        return resp;
    }

    @Transactional(readOnly = true)
    public byte[] importFailuresCsv(long importId) {
        CourseUserImportEntity row = courseUserImportRepository
                .findById(importId)
                .orElseThrow(() -> new IllegalArgumentException("导入记录不存在"));
        List<CourseUserImportResponse.ErrorItem> failures = List.of();
        if (row.getFailDetails() != null && !row.getFailDetails().isBlank()) {
            try {
                failures = objectMapper.readValue(row.getFailDetails(), FAIL_DETAILS_TYPE);
            } catch (Exception ignored) {
                failures = List.of();
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("行号,手机号,原因\n");
        if (failures != null) {
            for (CourseUserImportResponse.ErrorItem err : failures) {
                sb.append(err.getRow())
                        .append(',')
                        .append(csvCell(err.getPhone()))
                        .append(',')
                        .append(csvCell(err.getReason()))
                        .append('\n');
            }
        }
        return csvBytes(sb.toString());
    }

    private CourseUserListItem toItem(
            CourseUserEntity row,
            ViewerEntity viewer,
            List<TagRef> tags,
            String accessType,
            LocalDateTime now,
            double chapterCompletionRate) {
        String state = CourseEntitlementSupport.resolveState(
                row.getEntitlementStatus(), accessType, row.getEntitlementExpireTime(), now);
        String formattedExpire =
                row.getEntitlementExpireTime() == null ? null : TIME.format(row.getEntitlementExpireTime());
        String created = row.getCreatedTime() == null ? null : TIME.format(row.getCreatedTime());
        CourseUserListItem item = new CourseUserListItem();
        item.setId(row.getId());
        if (viewer != null) {
            item.setNickname(ViewerAdminService.displayNickname(viewer));
        } else {
            item.setNickname("用户_" + row.getViewerId());
        }
        item.setAvatar(viewer == null ? null : viewer.getAvatarUrl());
        item.setPhoneMasked(
                viewer == null ? null : ViewerAdminService.maskPhone(viewer.getPhone()));
        for (TagRef ref : tags) {
            item.getTagLabels().add(ref.name());
        }
        item.setChapterCompletionRate(chapterCompletionRate);
        item.setEntitlementStatus(row.getEntitlementStatus());
        item.setEntitlementState(state);
        item.setEntitlementStateLabel(CourseEntitlementSupport.stateLabel(state));
        String way = row.getAcquireWay() == null ? "purchase" : row.getAcquireWay();
        item.setAcquireWayLabel(CourseEntitlementSupport.acquireWayLabel(way));
        item.setFirstAcquireTime(created);
        item.setExpireTime(formattedExpire);
        item.setExpireDisplay(CourseEntitlementSupport.expireDisplay(accessType, formattedExpire));
        return item;
    }

    private Map<Long, ViewerEntity> loadViewers(List<CourseUserEntity> rows) {
        Set<Long> ids = rows.stream().map(CourseUserEntity::getViewerId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        Map<Long, ViewerEntity> map = new HashMap<>();
        for (ViewerEntity v : viewerRepository.findAllById(ids)) {
            map.put(v.getId(), v);
        }
        return map;
    }

    private CourseEntity requireCourse(String courseId) {
        String uuid = UuidIds.require(courseId, "课程 id 无效", "课程 id 须为本站 UUID");
        return courseRepository
                .findById(uuid)
                .filter(c -> c.getDeletedTime() == null)
                .orElseThrow(() -> new IllegalArgumentException("课程不存在"));
    }

    private void requireActiveEmployee(long userId) {
        SysUserAccountEntity user = userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("操作人无效"));
        if (user.getRsStatus() == null || user.getRsStatus() != 1) {
            throw new IllegalArgumentException("操作人无效");
        }
    }

    private Set<Long> resolveKeywordViewerIds(String type, String kw) {
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

    private void persistImportHistory(
            String courseId,
            long operatorId,
            LocalDateTime importTime,
            CourseUserImportResponse result,
            List<CourseUserImportResponse.ErrorItem> allFailures) {
        CourseUserImportEntity row = new CourseUserImportEntity();
        row.setCourseId(courseId);
        row.setImporterId(operatorId);
        row.setImportTime(importTime);
        row.setSuccessCount(result.getCreated() + result.getUpdated() + result.getSkipped());
        row.setFailCount(result.getFailed());
        try {
            row.setFailDetails(objectMapper.writeValueAsString(allFailures));
        } catch (Exception e) {
            throw new IllegalArgumentException("无法保存导入失败明细");
        }
        courseUserImportRepository.save(row);
        result.setImportId(row.getId());
    }

    private static void addImportError(
            CourseUserImportResponse result,
            List<CourseUserImportResponse.ErrorItem> allFailures,
            int row,
            String phone,
            String reason) {
        CourseUserImportResponse.ErrorItem err = new CourseUserImportResponse.ErrorItem();
        err.setRow(row);
        err.setPhone(phone);
        err.setReason(reason);
        allFailures.add(err);
        if (result.getErrors().size() < ERRORS_CAP) {
            result.getErrors().add(err);
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

    private static byte[] csvBytes(String content) {
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[UTF8_BOM.length + body.length];
        System.arraycopy(UTF8_BOM, 0, out, 0, UTF8_BOM.length);
        System.arraycopy(body, 0, out, UTF8_BOM.length, body.length);
        return out;
    }

    private static String csvCell(String value) {
        if (value == null) {
            return "";
        }
        String v = value.replace("\"", "\"\"");
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v + "\"";
        }
        return v;
    }

    private static String formatTime(LocalDateTime time) {
        return time == null ? null : TIME.format(time);
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

    private static Long parsePositiveLong(String raw) {
        try {
            long v = Long.parseLong(raw.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String normalizeStateFilter(String entitlementState) {
        if (entitlementState == null || entitlementState.isBlank()) {
            return null;
        }
        String s = entitlementState.trim();
        if (!STATE_FILTERS.contains(s)) {
            throw new IllegalArgumentException("entitlementState 无效");
        }
        return s;
    }

    private Map<Long, List<TagRef>> loadTagsByViewer(List<CourseUserEntity> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<Long> viewerIds = rows.stream().map(CourseUserEntity::getViewerId).distinct().toList();
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

    private record PhoneRow(int row, String phone) {}

    private Map<Long, Long> loadCompletedLessonCounts(String courseId, List<CourseUserEntity> rows) {
        Map<Long, Long> out = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            return out;
        }
        for (var p : progressRepository.findByCourseId(courseId)) {
            if (p.getParticipateStatus() != CourseLessonParticipateStatus.COMPLETED) {
                continue;
            }
            out.merge(p.getViewerId(), 1L, Long::sum);
        }
        return out;
    }

    private static double completionRate(long completed, long totalLessons) {
        if (totalLessons <= 0 || completed <= 0) {
            return 0D;
        }
        return Math.round(completed * 1000.0 / totalLessons) / 10.0;
    }

    private record PreparedList(
            List<CourseUserEntity> rows, String accessType, LocalDateTime now, String courseId) {}

    private record TagRef(long tagId, String name) {}
}
