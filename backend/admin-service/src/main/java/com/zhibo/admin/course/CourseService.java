package com.zhibo.admin.course;

import com.zhibo.admin.auth.AuthService;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.course.dto.CourseAuditItem;
import com.zhibo.admin.course.dto.CourseAuditListResponse;
import com.zhibo.admin.course.dto.CourseAuditRequest;
import com.zhibo.admin.course.dto.CourseAuditResultResponse;
import com.zhibo.admin.course.dto.CourseDetailResponse;
import com.zhibo.admin.course.dto.CourseIdResponse;
import com.zhibo.admin.course.dto.CourseListResponse;
import com.zhibo.admin.course.dto.CourseMaterialView;
import com.zhibo.admin.course.dto.CoursePerson;
import com.zhibo.admin.course.dto.CourseShelfRequest;
import com.zhibo.admin.course.dto.CourseShelfResponse;
import com.zhibo.admin.course.dto.CourseSummary;
import com.zhibo.admin.course.dto.CreateCourseRequest;
import com.zhibo.admin.course.dto.OutsideCourseItem;
import com.zhibo.admin.course.dto.OutsideCourseListResponse;
import com.zhibo.admin.course.dto.OutsideCourseOwner;
import com.zhibo.course.CourseAuditEntity;
import com.zhibo.course.CourseAuditRepository;
import com.zhibo.course.CourseEntity;
import com.zhibo.course.CourseMaterialEntity;
import com.zhibo.course.CourseMaterialId;
import com.zhibo.course.CourseMaterialRepository;
import com.zhibo.course.CourseOwnerEntity;
import com.zhibo.course.CourseOwnerId;
import com.zhibo.course.CourseOwnerRepository;
import com.zhibo.course.CourseRepository;
import com.zhibo.material.MaterialAudit;
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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

@Service
@RequiredArgsConstructor
public class CourseService {

    static final int OWNER_LIMIT = 20;
    static final int MATERIAL_LIMIT = 100;
    static final long COVER_MAX_BYTES = 5L * 1024 * 1024;
    static final int DETAIL_TEXT_LIMIT = 10_000;
    static final int DETAIL_HTML_LIMIT = 200_000;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> SHELF_VALUES = Set.of("pending", "on", "off", "banned");
    private static final Set<String> RISK_VALUES = Set.of("pending", "passed", "rejected");
    private static final Map<String, String> SORT_FIELDS = Map.of(
            "entitledUserCount", "entitledUserCount",
            "totalUserCount", "totalUserCount",
            "salesCount", "salesCount",
            "updatedTime", "updatedTime");

    private final CourseRepository courseRepository;
    private final CourseOwnerRepository ownerRepository;
    private final CourseMaterialRepository materialBindingRepository;
    private final CourseAuditRepository auditRepository;
    private final MaterialRepository materialRepository;
    private final SysUserAccountRepository userRepository;

    @Transactional
    public CourseIdResponse create(CreateCourseRequest request, long operatorId) {
        if (request.getAutoList() == null) {
            throw new IllegalArgumentException("是否自动上架不能为空");
        }
        String title = requireTitle(request.getTitle());
        String introduction = trimToNull(request.getIntroduction());
        if (introduction != null && introduction.length() > 256) {
            throw new IllegalArgumentException("课程简介不能超过256个字");
        }
        String detailHtml = normalizeDetail(request.getDetailHtml());
        MaterialEntity cover = requireCover(request.getCoverMaterialId());
        List<Long> ownerIds = parseOwnerIds(request.getOwnerIds());
        List<MaterialEntity> materials = parseMaterials(request.getMaterialIds());
        SysUserAccountEntity operator = requireOperator(operatorId);

        CourseEntity entity = new CourseEntity();
        entity.setId(UuidIds.newId());
        entity.setTitle(title);
        entity.setIntroduction(introduction);
        entity.setDetailHtml(detailHtml);
        entity.setCoverMaterialId(cover.getId());
        entity.setCoverUrl(cover.getUrl().trim());
        entity.setAutoList(request.getAutoList());
        entity.setShelfStatus("pending");
        entity.setRiskStatus("pending");
        entity.setContentCount(0);
        entity.setEntitledUserCount(0);
        entity.setTotalUserCount(0);
        entity.setSalesCount(0);
        courseRepository.save(entity);
        replaceOwners(entity.getId(), ownerIds);
        replaceMaterials(entity.getId(), materials);
        addAudit(entity.getId(), "submit", null, null, operator);
        return new CourseIdResponse(entity.getId());
    }

    public CourseListResponse list(
            int page,
            int pageSize,
            String keyword,
            String shelfStatus,
            String ownerName,
            String riskStatus,
            String sort,
            String order) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
        String keywordFilter = trimToNull(keyword);
        String shelfFilter = normalizeEnum(shelfStatus, SHELF_VALUES, "shelfStatus");
        String riskFilter = normalizeEnum(riskStatus, RISK_VALUES, "riskStatus");
        String ownerFilter = trimToNull(ownerName);
        PageRequest pageable = PageRequest.of(page - 1, pageSize, resolveSort(sort, order));

        Page<CourseEntity> result;
        if (ownerFilter == null) {
            result = courseRepository.search(keywordFilter, shelfFilter, riskFilter, pageable);
        } else {
            List<Long> userIds = userRepository.findByUserNameAndRsStatus(ownerFilter, 1).stream()
                    .map(SysUserAccountEntity::getId)
                    .toList();
            List<String> courseIds = userIds.isEmpty()
                    ? List.of()
                    : ownerRepository.findCourseIdsByUserIdIn(userIds);
            if (courseIds.isEmpty()) {
                return emptyList(page, pageSize);
            }
            result = courseRepository.searchByIds(courseIds, keywordFilter, shelfFilter, riskFilter, pageable);
        }

        List<CourseSummary> summaries = result.getContent().stream().map(this::toSummary).toList();
        attachOwners(summaries);
        CourseListResponse response = new CourseListResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(result.getTotalElements());
        response.setList(summaries);
        return response;
    }

    /**
     * 对外列表：只含已上架且风控已通过、未软删的课程。不带管理端计数字段与操作标记。
     */
    public OutsideCourseListResponse outsideList(int page, int pageSize, String keyword) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
        String keywordFilter = trimToNull(keyword);
        PageRequest pageable = PageRequest.of(page - 1, pageSize, Sort.by("listedTime").descending());
        Page<CourseEntity> result = courseRepository.search(keywordFilter, "on", "passed", pageable);

        List<OutsideCourseItem> items = new ArrayList<>();
        for (CourseEntity entity : result.getContent()) {
            if (!visibleOutside(entity)) {
                continue;
            }
            items.add(toOutsideItem(entity));
        }
        attachOutsideOwners(items);

        OutsideCourseListResponse response = new OutsideCourseListResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(result.getTotalElements());
        response.setList(items);
        return response;
    }

    public CourseDetailResponse detail(String id) {
        CourseEntity entity = requireCourse(id);
        CourseDetailResponse detail = new CourseDetailResponse();
        copySummary(entity, detail);
        detail.setIntroduction(entity.getIntroduction());
        detail.setDetailHtml(entity.getDetailHtml());
        detail.setCoverMaterialId(entity.getCoverMaterialId());
        attachOwners(List.of(detail));
        detail.setMaterials(loadMaterials(entity.getId()));
        return detail;
    }

    @Transactional
    public CourseIdResponse delete(String id) {
        CourseEntity entity = requireCourse(id);
        if (!canDelete(entity)) {
            throw new IllegalArgumentException("仅内容数和总销量均为 0 的课程可以删除");
        }
        entity.setDeletedTime(LocalDateTime.now());
        courseRepository.save(entity);
        return new CourseIdResponse(entity.getId());
    }

    @Transactional
    public CourseShelfResponse shelf(String id, CourseShelfRequest request) {
        if (request == null || request.getListed() == null) {
            throw new IllegalArgumentException("listed 不能为空");
        }
        CourseEntity entity = requireCourse(id);
        String current = entity.getShelfStatus();
        if ("banned".equals(current)) {
            throw new IllegalArgumentException("强制封禁不可上下架");
        }
        if (request.getListed()) {
            if (!"passed".equals(entity.getRiskStatus())) {
                throw new IllegalArgumentException("风控未通过不可上架");
            }
            if ("on".equals(current)) {
                throw new IllegalArgumentException("已经上架");
            }
            if (!"pending".equals(current) && !"off".equals(current)) {
                throw new IllegalArgumentException("当前状态不可上架");
            }
            entity.setShelfStatus("on");
            entity.setListedTime(LocalDateTime.now());
        } else {
            if (!"on".equals(current)) {
                throw new IllegalArgumentException("只有已上架可以下架");
            }
            entity.setShelfStatus("off");
        }
        courseRepository.save(entity);
        return new CourseShelfResponse(entity.getId(), entity.getShelfStatus());
    }

    public CourseAuditListResponse audits(String id) {
        CourseEntity entity = requireCourse(id);
        CourseAuditListResponse response = new CourseAuditListResponse();
        List<CourseAuditEntity> rows =
                auditRepository.findByCourseIdOrderByCreatedTimeDesc(entity.getId());
        Set<Long> opIds = rows.stream()
                .map(CourseAuditEntity::getOperatorId)
                .filter(Objects::nonNull)
                .filter(opId -> opId > 0)
                .collect(Collectors.toSet());
        Map<Long, String> deptByUser = new HashMap<>();
        if (!opIds.isEmpty()) {
            for (SysUserAccountEntity user : userRepository.findAllById(opIds)) {
                String dept = AuthService.resolveDepartmentName(user);
                if (dept != null && !dept.isBlank()) {
                    deptByUser.put(user.getId(), dept);
                }
            }
        }
        List<CourseAuditItem> list = new ArrayList<>();
        boolean hasSubmit = false;
        for (CourseAuditEntity row : rows) {
            CourseAuditItem item = toAuditItem(row, deptByUser.get(row.getOperatorId()));
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
    public CourseAuditResultResponse audit(String id, CourseAuditRequest request, long operatorId) {
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

        CourseEntity entity = requireCourse(id);
        SysUserAccountEntity operator = requireOperator(operatorId);
        if ("rejected".equals(result)) {
            entity.setRiskStatus("rejected");
            entity.setShelfStatus("banned");
        } else {
            entity.setRiskStatus("passed");
            if (Boolean.TRUE.equals(entity.getAutoList())) {
                entity.setShelfStatus("on");
                entity.setListedTime(LocalDateTime.now());
            } else if ("banned".equals(entity.getShelfStatus())) {
                entity.setShelfStatus("pending");
            }
        }
        courseRepository.save(entity);
        addAudit(entity.getId(), "audit", result, reason, operator);
        return new CourseAuditResultResponse(entity.getId(), entity.getRiskStatus(), entity.getShelfStatus());
    }

    private CourseListResponse emptyList(int page, int pageSize) {
        CourseListResponse response = new CourseListResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(0);
        response.setList(List.of());
        return response;
    }

    private CourseEntity requireCourse(String id) {
        String uuid = requireUuid(id);
        CourseEntity entity = courseRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("课程不存在"));
        if (entity.getDeletedTime() != null) {
            throw new IllegalArgumentException("课程不存在");
        }
        return entity;
    }

    private static String requireUuid(String id) {
        return UuidIds.require(id, "课程 id 无效", "课程 id 须为本站 UUID");
    }

    private SysUserAccountEntity requireOperator(long operatorId) {
        return userRepository.findById(operatorId)
                .filter(user -> Integer.valueOf(1).equals(user.getRsStatus()))
                .orElseThrow(() -> new IllegalArgumentException("当前员工不存在"));
    }

    private static String requireTitle(String raw) {
        String title = raw == null ? "" : raw.trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException("请输入课程名称");
        }
        if (title.length() > 45) {
            throw new IllegalArgumentException("课程名称不能超过45个字");
        }
        return title;
    }

    private MaterialEntity requireCover(String materialId) {
        String id = materialId == null ? "" : materialId.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("请选择课程封面");
        }
        MaterialEntity material = materialRepository.findById(id)
                .filter(item -> item.getDeletedTime() == null)
                .orElseThrow(() -> new IllegalArgumentException("封面素材不存在"));
        if (!"image".equals(material.getType())) {
            throw new IllegalArgumentException("封面须为图片素材");
        }
        if (!passed(material)) {
            throw new IllegalArgumentException("封面须来自素材中心已审核通过的图片");
        }
        if (material.getFileSize() == null || material.getFileSize() <= 0 || material.getFileSize() > COVER_MAX_BYTES) {
            throw new IllegalArgumentException("封面大小须不超过 5MB");
        }
        resolveImageMime(material);
        if (material.getUrl() == null || material.getUrl().isBlank()) {
            throw new IllegalArgumentException("封面素材暂无可用地址");
        }
        return material;
    }

    private static String resolveImageMime(MaterialEntity material) {
        String mime = material.getMimeType();
        if (mime != null) {
            String normalized = mime.toLowerCase(Locale.ROOT);
            if ("image/jpg".equals(normalized) || "image/jpeg".equals(normalized)) {
                return "image/jpeg";
            }
            if ("image/png".equals(normalized)) {
                return "image/png";
            }
        }
        String name = material.getName() == null ? "" : material.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) {
            return "image/png";
        }
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        throw new IllegalArgumentException("封面只支持 JPG/PNG");
    }

    private List<Long> parseOwnerIds(List<Long> rawIds) {
        if (rawIds == null || rawIds.isEmpty()) {
            throw new IllegalArgumentException("归属人至少选择1人");
        }
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Long raw : rawIds) {
            if (raw == null || raw <= 0L) {
                throw new IllegalArgumentException("归属人无效");
            }
            ids.add(raw);
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("归属人至少选择1人");
        }
        if (ids.size() > OWNER_LIMIT) {
            throw new IllegalArgumentException("归属人不能超过" + OWNER_LIMIT + "个");
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

    private List<MaterialEntity> parseMaterials(List<String> rawIds) {
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
        if (ids.size() > MATERIAL_LIMIT) {
            throw new IllegalArgumentException("课件不能超过" + MATERIAL_LIMIT + "个");
        }
        Map<String, MaterialEntity> found = materialRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(MaterialEntity::getId, item -> item, (a, b) -> a));
        List<MaterialEntity> materials = new ArrayList<>();
        for (String id : ids) {
            MaterialEntity material = found.get(id);
            if (material == null || material.getDeletedTime() != null) {
                throw new IllegalArgumentException("课件无效");
            }
            if ("article".equals(material.getType())) {
                throw new IllegalArgumentException("课件不支持文章");
            }
            if (!Set.of("image", "video", "document").contains(material.getType())) {
                throw new IllegalArgumentException("课件无效");
            }
            if (!passed(material)) {
                throw new IllegalArgumentException("课件须已审核通过");
            }
            if (material.getUrl() == null || material.getUrl().isBlank()) {
                throw new IllegalArgumentException("课件无效");
            }
            materials.add(material);
        }
        return materials;
    }

    private static boolean passed(MaterialEntity material) {
        return MaterialAudit.bothPassed(material);
    }

    private static String normalizeDetail(String html) {
        if (html == null) {
            return null;
        }
        String trimmed = html.trim();
        if (trimmed.isEmpty() || "<p><br></p>".equals(trimmed) || "<p></p>".equals(trimmed) || "<br>".equals(trimmed)) {
            return null;
        }
        if (trimmed.length() > DETAIL_HTML_LIMIT) {
            throw new IllegalArgumentException("课程详情内容过长");
        }
        String text = trimmed.replaceAll("(?i)<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replaceAll("&[a-zA-Z#0-9]+;", "");
        if (text.length() > DETAIL_TEXT_LIMIT) {
            throw new IllegalArgumentException("课程详情不能超过10000个字");
        }
        return trimmed;
    }

    private void replaceOwners(String courseId, List<Long> ownerIds) {
        ownerRepository.deleteByIdCourseId(courseId);
        List<CourseOwnerEntity> rows = new ArrayList<>();
        int order = 0;
        for (Long userId : ownerIds) {
            CourseOwnerEntity row = new CourseOwnerEntity();
            row.setId(new CourseOwnerId(courseId, userId));
            row.setSortOrder(order++);
            rows.add(row);
        }
        ownerRepository.saveAll(rows);
    }

    private void replaceMaterials(String courseId, List<MaterialEntity> materials) {
        materialBindingRepository.deleteByIdCourseId(courseId);
        if (materials.isEmpty()) {
            return;
        }
        List<CourseMaterialEntity> rows = new ArrayList<>();
        int order = 0;
        for (MaterialEntity material : materials) {
            CourseMaterialEntity row = new CourseMaterialEntity();
            row.setId(new CourseMaterialId(courseId, material.getId()));
            row.setName(material.getName() == null ? "" : material.getName());
            row.setUrl(material.getUrl().trim());
            row.setType(material.getType());
            row.setFileSize(material.getFileSize());
            row.setMimeType(material.getMimeType());
            row.setSortOrder(order++);
            rows.add(row);
        }
        materialBindingRepository.saveAll(rows);
    }

    private void addAudit(String courseId, String action, String result, String reason, SysUserAccountEntity operator) {
        CourseAuditEntity row = new CourseAuditEntity();
        row.setCourseId(courseId);
        row.setAction(action);
        row.setResult(result);
        row.setReason(reason);
        row.setOperatorId(operator.getId());
        row.setOperatorName(operator.getUserName() == null ? "" : operator.getUserName());
        auditRepository.save(row);
    }

    private void attachOwners(List<? extends CourseSummary> summaries) {
        if (summaries.isEmpty()) {
            return;
        }
        List<String> ids = summaries.stream().map(CourseSummary::getId).toList();
        Map<String, List<CourseOwnerEntity>> owners = ownerRepository.findByCourseIds(ids).stream()
                .collect(Collectors.groupingBy(row -> row.getId().getCourseId(), LinkedHashMap::new, Collectors.toList()));
        Set<Long> userIds = owners.values().stream()
                .flatMap(List::stream)
                .map(row -> row.getId().getUserId())
                .collect(Collectors.toSet());
        Map<Long, SysUserAccountEntity> users = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(SysUserAccountEntity::getId, user -> user, (a, b) -> a));
        for (CourseSummary summary : summaries) {
            List<CoursePerson> people = new ArrayList<>();
            for (CourseOwnerEntity row : owners.getOrDefault(summary.getId(), List.of())) {
                SysUserAccountEntity user = users.get(row.getId().getUserId());
                if (user == null) {
                    continue;
                }
                people.add(toPerson(user));
            }
            summary.setOwners(people);
        }
    }

    private void attachOutsideOwners(List<OutsideCourseItem> items) {
        for (OutsideCourseItem item : items) {
            if (item.getOwners() == null) {
                item.setOwners(new ArrayList<>());
            }
        }
        if (items.isEmpty()) {
            return;
        }
        List<String> ids = items.stream().map(OutsideCourseItem::getId).toList();
        Map<String, List<CourseOwnerEntity>> owners = ownerRepository.findByCourseIds(ids).stream()
                .collect(Collectors.groupingBy(row -> row.getId().getCourseId(), LinkedHashMap::new, Collectors.toList()));
        Set<Long> userIds = owners.values().stream()
                .flatMap(List::stream)
                .map(row -> row.getId().getUserId())
                .collect(Collectors.toSet());
        Map<Long, SysUserAccountEntity> users = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(SysUserAccountEntity::getId, user -> user, (a, b) -> a));
        for (OutsideCourseItem item : items) {
            List<OutsideCourseOwner> people = new ArrayList<>();
            for (CourseOwnerEntity row : owners.getOrDefault(item.getId(), List.of())) {
                SysUserAccountEntity user = users.get(row.getId().getUserId());
                if (user == null) {
                    continue;
                }
                OutsideCourseOwner person = new OutsideCourseOwner();
                person.setId(user.getId());
                person.setUserName(user.getUserName());
                people.add(person);
            }
            item.setOwners(people);
        }
    }

    private static OutsideCourseItem toOutsideItem(CourseEntity entity) {
        OutsideCourseItem item = new OutsideCourseItem();
        item.setId(entity.getId());
        item.setTitle(entity.getTitle());
        item.setCoverUrl(trimToNull(entity.getCoverUrl()));
        item.setIntroduction(trimToNull(entity.getIntroduction()));
        item.setListedTime(formatTime(entity.getListedTime()));
        item.setUpdatedTime(formatTime(entity.getUpdatedTime()));
        return item;
    }

    private static boolean visibleOutside(CourseEntity entity) {
        return entity.getDeletedTime() == null
                && "on".equals(entity.getShelfStatus())
                && "passed".equals(entity.getRiskStatus());
    }

    private List<CourseMaterialView> loadMaterials(String courseId) {
        List<CourseMaterialView> views = new ArrayList<>();
        for (CourseMaterialEntity row : materialBindingRepository.findByCourseIds(List.of(courseId))) {
            CourseMaterialView view = new CourseMaterialView();
            view.setId(row.getId().getMaterialId());
            view.setName(row.getName());
            view.setUrl(row.getUrl());
            view.setType(row.getType());
            view.setFileSize(row.getFileSize());
            view.setMimeType(row.getMimeType());
            views.add(view);
        }
        return views;
    }

    private CourseSummary toSummary(CourseEntity entity) {
        CourseSummary summary = new CourseSummary();
        copySummary(entity, summary);
        return summary;
    }

    private static void copySummary(CourseEntity entity, CourseSummary summary) {
        summary.setId(entity.getId());
        summary.setTitle(entity.getTitle());
        summary.setCoverUrl(entity.getCoverUrl());
        summary.setShelfStatus(entity.getShelfStatus());
        summary.setRiskStatus(entity.getRiskStatus());
        summary.setAutoList(entity.getAutoList());
        summary.setListedTime(formatTime(entity.getListedTime()));
        summary.setContentCount(entity.getContentCount());
        summary.setEntitledUserCount(entity.getEntitledUserCount());
        summary.setTotalUserCount(entity.getTotalUserCount());
        summary.setSalesCount(entity.getSalesCount());
        summary.setUpdatedTime(formatTime(entity.getUpdatedTime()));
        summary.setCreatedTime(formatTime(entity.getCreatedTime()));
        summary.setCanList(canList(entity));
        summary.setCanUnlist("on".equals(entity.getShelfStatus()));
        summary.setCanDelete(canDelete(entity));
    }

    static boolean canList(CourseEntity entity) {
        return "passed".equals(entity.getRiskStatus())
                && ("pending".equals(entity.getShelfStatus()) || "off".equals(entity.getShelfStatus()));
    }

    static boolean canDelete(CourseEntity entity) {
        return entity.getContentCount() == 0 && entity.getSalesCount() == 0;
    }

    private CoursePerson toPerson(SysUserAccountEntity user) {
        CoursePerson person = new CoursePerson();
        person.setId(user.getId());
        person.setUserName(user.getUserName());
        person.setAvatar(AuthService.resolveAvatar(user));
        return person;
    }

    private CourseAuditItem toAuditItem(CourseAuditEntity row, String departmentName) {
        CourseAuditItem item = new CourseAuditItem();
        item.setId(row.getId());
        String action = row.getAction() == null || row.getAction().isBlank() ? "audit" : row.getAction();
        item.setAction(action);
        item.setOperatorId(row.getOperatorId());
        item.setOperatorName(row.getOperatorName());
        item.setDepartmentName(departmentName);
        item.setCreatedTime(formatTime(row.getCreatedTime()));
        if (!"submit".equals(action)) {
            item.setResult(row.getResult());
            if ("rejected".equals(row.getResult())) {
                item.setReason(row.getReason());
            }
        }
        return item;
    }

    private CourseAuditItem syntheticSubmit(CourseEntity entity) {
        CourseAuditItem item = new CourseAuditItem();
        item.setId("synthetic-submit");
        item.setAction("submit");
        item.setOperatorId(0L);
        item.setOperatorName("系统");
        item.setCreatedTime(formatTime(entity.getCreatedTime()));
        return item;
    }

    private static Sort resolveSort(String sort, String order) {
        String field = sort == null || sort.isBlank() ? "updatedTime" : sort.trim();
        String direction = order == null || order.isBlank() ? "desc" : order.trim().toLowerCase(Locale.ROOT);
        String property = SORT_FIELDS.get(field);
        if (property == null) {
            throw new IllegalArgumentException("sort 无效");
        }
        if (!"asc".equals(direction) && !"desc".equals(direction)) {
            throw new IllegalArgumentException("order 无效");
        }
        return "asc".equals(direction) ? Sort.by(property).ascending() : Sort.by(property).descending();
    }

    private static String normalizeEnum(String raw, Set<String> allowed, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim();
        if (!allowed.contains(value)) {
            throw new IllegalArgumentException(label + " 无效");
        }
        return value;
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String formatTime(LocalDateTime time) {
        return time == null ? null : time.format(TIME);
    }
}
