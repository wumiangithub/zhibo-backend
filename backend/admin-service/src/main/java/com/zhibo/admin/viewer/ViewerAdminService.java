package com.zhibo.admin.viewer;

import com.zhibo.admin.viewer.dto.LearningCourseListItem;
import com.zhibo.admin.viewer.dto.LearningCourseListResponse;
import com.zhibo.admin.viewer.dto.LearningSummaryListItem;
import com.zhibo.admin.viewer.dto.LearningSummaryListResponse;
import com.zhibo.admin.viewer.dto.UpdateViewerBlacklistResponse;
import com.zhibo.admin.viewer.dto.UpdateViewerTagsResponse;
import com.zhibo.admin.viewer.dto.ViewerListItem;
import com.zhibo.admin.viewer.dto.ViewerListResponse;
import com.zhibo.course.CourseCatalogContentKind;
import com.zhibo.course.CourseCatalogNodeEntity;
import com.zhibo.course.CourseCatalogNodeRepository;
import com.zhibo.course.CourseLessonProgressEntity;
import com.zhibo.course.CourseLessonProgressRepository;
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialRepository;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerDisplayNames;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerStatus;
import com.zhibo.viewer.ViewerTagEntity;
import com.zhibo.viewer.ViewerTagMemberEntity;
import com.zhibo.viewer.ViewerTagMemberRepository;
import com.zhibo.viewer.ViewerTagRepository;
import com.zhibo.viewer.ViewerTagType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 顶栏用户管理 · 用户列表 / 学习统计（D79 / D82 / D83 / D112）。 */
@Service
@RequiredArgsConstructor
public class ViewerAdminService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> SORT_KEYS = Set.of(
            "createdTime",
            "lastVisitTime",
            "consumeCount",
            "consumeAmount",
            "lastConsumeTime",
            "totalLearnDuration",
            "todayLearnDuration",
            "completedCourseCount",
            "firstPayTime");
    private static final Set<String> LEARNING_SORT_KEYS = Set.of(
            "id", "totalLearnDuration", "totalLearnDays", "viewedCourseCount", "completedCourseCount");
    private static final Set<String> LEARN_DURATION_OPS = Set.of("gt", "lte");
    private static final Set<String> KEYWORD_TYPES = Set.of("nickname", "id", "phone");
    /** any=满足其一 / all=同时满足 / none=不包含（都不带所选标签） */
    private static final Set<String> TAG_MATCH_MODES = Set.of("any", "all", "none");

    private final ViewerRepository viewerRepository;
    private final ViewerTagMemberRepository viewerTagMemberRepository;
    private final ViewerTagRepository viewerTagRepository;
    private final CourseLessonProgressRepository courseLessonProgressRepository;
    private final CourseCatalogNodeRepository courseCatalogNodeRepository;
    private final MaterialRepository materialRepository;

    @Transactional(readOnly = true)
    public ViewerListResponse list(
            int page,
            int pageSize,
            String keywordType,
            String keyword,
            Double consumeAmountMin,
            Double consumeAmountMax,
            Integer consumeCountMin,
            Integer consumeCountMax,
            List<Long> tagIds,
            String tagMatch,
            String sort,
            String order) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }

        String type = resolveKeywordType(keywordType);
        String kw = keyword == null ? "" : keyword.trim();
        List<Long> filterTagIds = normalizeFilterTagIds(tagIds);
        String match = resolveTagMatch(tagMatch);

        if (placeholderFilterMiss(consumeAmountMin, consumeAmountMax, consumeCountMin, consumeCountMax)) {
            return emptyPage(page, pageSize);
        }

        if ("id".equals(type) && kw.length() > 16) {
            return emptyPage(page, pageSize);
        }
        if ("id".equals(type) && !kw.isEmpty() && parsePositiveLong(kw) == null) {
            return emptyPage(page, pageSize);
        }

        Sort sortSpec = resolveSort(sort, order);
        Specification<ViewerEntity> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (!kw.isEmpty()) {
                switch (type) {
                    case "id" -> preds.add(cb.equal(root.get("username"), kw));
                    case "phone" -> preds.add(cb.like(root.get("phone"), "%" + kw + "%"));
                    default -> preds.add(cb.like(root.get("nickname"), "%" + kw + "%"));
                }
            }
            if (!filterTagIds.isEmpty() && query != null) {
                if ("all".equals(match)) {
                    for (Long tagId : filterTagIds) {
                        preds.add(existsMember(root, query, cb, List.of(tagId)));
                    }
                } else if ("none".equals(match)) {
                    preds.add(cb.not(existsMember(root, query, cb, filterTagIds)));
                } else {
                    preds.add(existsMember(root, query, cb, filterTagIds));
                }
            }
            if (preds.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(preds.toArray(Predicate[]::new));
        };

        Page<ViewerEntity> result = viewerRepository.findAll(spec, PageRequest.of(page - 1, pageSize, sortSpec));
        Map<String, List<TagRef>> tagsByViewer = loadTagsByViewer(result.getContent());

        ViewerListResponse response = new ViewerListResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(result.getTotalElements());
        for (ViewerEntity row : result.getContent()) {
            response.getList().add(toItem(row, tagsByViewer.getOrDefault(row.getUsername(), List.of())));
        }
        return response;
    }

    /**
     * 学习统计 · 汇总。学习时长本轮恒 0：{@code gt} 空列表，{@code lte} 不挡。
     */
    @Transactional(readOnly = true)
    public LearningSummaryListResponse learningSummary(
            int page,
            int pageSize,
            String keywordType,
            String keyword,
            List<Long> tagIds,
            String tagMatch,
            String learnDurationOp,
            Integer learnDurationSec,
            String sort,
            String order) {
        requirePage(page, pageSize);
        if (learnDurationFilterMiss(learnDurationOp, learnDurationSec)) {
            return emptyLearningSummary(page, pageSize);
        }

        String type = resolveKeywordType(keywordType);
        String kw = keyword == null ? "" : keyword.trim();
        List<Long> filterTagIds = normalizeFilterTagIds(tagIds);
        String match = resolveTagMatch(tagMatch);

        if ("id".equals(type) && !kw.isEmpty() && parsePositiveLong(kw) == null) {
            return emptyLearningSummary(page, pageSize);
        }

        Sort sortSpec = resolveLearningSort(sort, order);
        Specification<ViewerEntity> spec = viewerKeywordTagSpec(type, kw, filterTagIds, match);
        Page<ViewerEntity> result = viewerRepository.findAll(spec, PageRequest.of(page - 1, pageSize, sortSpec));
        Map<String, List<TagRef>> tagsByViewer = loadTagsByViewer(result.getContent());

        LearningSummaryListResponse response = new LearningSummaryListResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(result.getTotalElements());
        for (ViewerEntity row : result.getContent()) {
            response.getList().add(toLearningSummaryItem(row, tagsByViewer.getOrDefault(row.getUsername(), List.of())));
        }
        return response;
    }

    /**
     * 学习统计 · 课程数据（D112）：小节维度，读 {@code course_lesson_progress}。
     */
    @Transactional(readOnly = true)
    public LearningCourseListResponse learningCourses(
            int page,
            int pageSize,
            String keywordType,
            String keyword,
            List<Long> tagIds,
            String tagMatch) {
        requirePage(page, pageSize);
        String type = resolveKeywordType(keywordType);
        String match = resolveTagMatch(tagMatch);
        List<Long> filterTagIds = normalizeFilterTagIds(tagIds);
        String kw = keyword == null ? "" : keyword.trim();
        if ("id".equals(type) && !kw.isEmpty() && parsePositiveLong(kw) == null) {
            return emptyLearningCourses(page, pageSize);
        }

        boolean filterViewers = !kw.isEmpty() || !filterTagIds.isEmpty();
        List<String> viewerUsernameFilter = null;
        if (filterViewers) {
            List<ViewerEntity> matched =
                    viewerRepository.findAll(viewerKeywordTagSpec(type, kw, filterTagIds, match));
            if (matched.isEmpty()) {
                return emptyLearningCourses(page, pageSize);
            }
            viewerUsernameFilter = matched.stream()
                    .map(ViewerEntity::getUsername)
                    .filter(u -> u != null && !u.isBlank())
                    .map(String::trim)
                    .toList();
        }

        List<String> finalViewerUsernames = viewerUsernameFilter;
        Specification<CourseLessonProgressEntity> progressSpec = (root, query, cb) -> {
            if (finalViewerUsernames == null) {
                return cb.conjunction();
            }
            return root.get("username").in(finalViewerUsernames);
        };
        Page<CourseLessonProgressEntity> result = courseLessonProgressRepository.findAll(
                progressSpec, PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "id")));

        List<CourseLessonProgressEntity> rows = result.getContent();
        Set<String> viewerUsernames = rows.stream().map(CourseLessonProgressEntity::getUsername).collect(Collectors.toSet());
        Set<Long> lessonIds =
                rows.stream().map(CourseLessonProgressEntity::getLessonNodeId).collect(Collectors.toSet());

        Map<String, ViewerEntity> viewersById = new HashMap<>();
        if (!viewerUsernames.isEmpty()) {
            for (ViewerEntity v : viewerRepository.findByUsernameIn(viewerUsernames)) {
                viewersById.put(v.getUsername(), v);
            }
        }
        Map<String, List<TagRef>> tagsByViewer =
                loadTagsByViewer(new ArrayList<>(viewersById.values()));

        Map<Long, CourseCatalogNodeEntity> nodesById = new HashMap<>();
        if (!lessonIds.isEmpty()) {
            for (CourseCatalogNodeEntity n : courseCatalogNodeRepository.findAllById(lessonIds)) {
                nodesById.put(n.getId(), n);
            }
        }
        Set<String> materialIds = new HashSet<>();
        for (CourseCatalogNodeEntity n : nodesById.values()) {
            if (n.getContentKind() != null
                    && n.getContentKind() == CourseCatalogContentKind.VIDEO
                    && n.getContentId() != null
                    && !n.getContentId().isBlank()) {
                materialIds.add(n.getContentId().trim());
            }
        }
        Map<String, MaterialEntity> materialsById = new HashMap<>();
        if (!materialIds.isEmpty()) {
            for (MaterialEntity m : materialRepository.findAllById(materialIds)) {
                materialsById.put(m.getId(), m);
            }
        }

        LearningCourseListResponse response = new LearningCourseListResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(result.getTotalElements());
        for (CourseLessonProgressEntity row : rows) {
            ViewerEntity viewer = viewersById.get(row.getUsername());
            CourseCatalogNodeEntity node = nodesById.get(row.getLessonNodeId());
            response.getList()
                    .add(toLearningCourseItem(
                            row,
                            viewer,
                            tagsByViewer.getOrDefault(row.getUsername(), List.of()),
                            node,
                            materialsById));
        }
        return response;
    }

    private static LearningCourseListItem toLearningCourseItem(
            CourseLessonProgressEntity row,
            ViewerEntity viewer,
            List<TagRef> tags,
            CourseCatalogNodeEntity node,
            Map<String, MaterialEntity> materialsById) {
        LearningCourseListItem item = new LearningCourseListItem();
        item.setId(row.getId());
        item.setUsername(row.getUsername());
        if (viewer != null) {
            item.setNickname(displayNickname(viewer));
            if (viewer.getAvatarUrl() != null && !viewer.getAvatarUrl().isBlank()) {
                item.setAvatarUrl(viewer.getAvatarUrl().trim());
            }
        } else {
            item.setNickname("用户_" + row.getUsername());
        }
        List<Long> tagIds = new ArrayList<>();
        List<String> tagLabels = new ArrayList<>();
        for (TagRef ref : tags) {
            tagIds.add(ref.tagId());
            tagLabels.add(ref.name());
        }
        item.setTagIds(tagIds);
        item.setTagLabels(tagLabels);
        item.setCourseId(row.getCourseId());
        item.setLessonNodeId(row.getLessonNodeId());
        if (node != null && node.getTitle() != null && !node.getTitle().isBlank()) {
            item.setLessonName(node.getTitle().trim());
        }
        long learnSec = row.getLearnDurationSec();
        item.setTotalLearnDurationSec(learnSec > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) learnSec);
        item.setProgressPercent(Math.max(0, Math.min(100, row.getProgressPercent())));
        if (row.getFirstParticipateTime() != null) {
            item.setFirstLearnTime(TIME_FMT.format(row.getFirstParticipateTime()));
        }
        if (row.getCompletedTime() != null) {
            item.setCompletedTime(TIME_FMT.format(row.getCompletedTime()));
        }
        if (node != null) {
            String typeLabel = contentKindLabel(node.getContentKind());
            if (typeLabel != null) {
                item.setLessonType(typeLabel);
            }
            item.setLessonDurationSec(resolveLessonDurationSec(node, materialsById));
        }
        return item;
    }

    private static String contentKindLabel(Integer kind) {
        if (kind == null) {
            return null;
        }
        return switch (kind) {
            case CourseCatalogContentKind.LIVE -> "直播";
            case CourseCatalogContentKind.IMAGE -> "图片";
            case CourseCatalogContentKind.VIDEO -> "视频";
            case CourseCatalogContentKind.DOCUMENT -> "文档";
            case CourseCatalogContentKind.ARTICLE -> "文章";
            default -> null;
        };
    }

    /** 视频素材 meta（mm:ss / h:mm:ss）→ 秒；其它类型或解析失败 → 0。 */
    static int resolveLessonDurationSec(CourseCatalogNodeEntity node, Map<String, MaterialEntity> materialsById) {
        if (node == null
                || node.getContentKind() == null
                || node.getContentKind() != CourseCatalogContentKind.VIDEO) {
            return 0;
        }
        if (node.getContentId() == null || node.getContentId().isBlank()) {
            return 0;
        }
        MaterialEntity material = materialsById.get(node.getContentId().trim());
        if (material == null) {
            return 0;
        }
        return parseDurationMetaToSec(material.getMeta());
    }

    /** 解析素材视频时长 meta：{@code mm:ss} 或 {@code h:mm:ss}。 */
    static int parseDurationMetaToSec(String meta) {
        if (meta == null || meta.isBlank()) {
            return 0;
        }
        String raw = meta.trim();
        String[] parts = raw.split(":");
        try {
            if (parts.length == 2) {
                int m = Integer.parseInt(parts[0]);
                int s = Integer.parseInt(parts[1]);
                if (m < 0 || s < 0 || s > 59) {
                    return 0;
                }
                return m * 60 + s;
            }
            if (parts.length == 3) {
                int h = Integer.parseInt(parts[0]);
                int m = Integer.parseInt(parts[1]);
                int s = Integer.parseInt(parts[2]);
                if (h < 0 || m < 0 || m > 59 || s < 0 || s > 59) {
                    return 0;
                }
                return h * 3600 + m * 60 + s;
            }
        } catch (NumberFormatException ignored) {
            return 0;
        }
        return 0;
    }

    private static Predicate existsMember(
            Root<ViewerEntity> root,
            jakarta.persistence.criteria.CriteriaQuery<?> query,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            List<Long> tagIds) {
        Subquery<Long> sq = query.subquery(Long.class);
        Root<ViewerTagMemberEntity> m = sq.from(ViewerTagMemberEntity.class);
        sq.select(m.get("id"));
        sq.where(
                cb.equal(m.get("username"), root.get("username")),
                m.get("tagId").in(tagIds));
        return cb.exists(sq);
    }

    @Transactional
    public UpdateViewerBlacklistResponse updateBlacklist(
            String username, int banComment, int banPurchase, int banAccess) {
        requireFlag(banComment, "banComment");
        requireFlag(banPurchase, "banPurchase");
        requireFlag(banAccess, "banAccess");
        ViewerEntity viewer = requireViewer(username);
        viewer.setBanComment(banComment);
        viewer.setBanPurchase(banPurchase);
        viewer.setBanAccess(banAccess);
        viewer.setStatus(banAccess == 1 ? ViewerStatus.DISABLED : ViewerStatus.NORMAL);
        viewer.setUpdatedTime(LocalDateTime.now());
        viewerRepository.save(viewer);
        return new UpdateViewerBlacklistResponse(
                viewer.getUsername(),
                viewer.getBanComment(),
                viewer.getBanPurchase(),
                viewer.getBanAccess(),
                viewer.getStatus());
    }

    @Transactional
    public UpdateViewerTagsResponse updateTags(String username, List<Long> tagIds) {
        ViewerEntity viewer = requireViewer(username);
        String memberUsername = viewer.getUsername();
        if (memberUsername == null || memberUsername.isBlank()) {
            throw new IllegalArgumentException("观众 username 无效");
        }
        List<Long> requested = normalizeTagIds(tagIds);
        Map<Long, ViewerTagEntity> tagMap = loadActiveTags(requested);
        for (Long tagId : requested) {
            ViewerTagEntity tag = tagMap.get(tagId);
            if (tag == null) {
                throw new IllegalArgumentException("标签不存在: " + tagId);
            }
            if (tag.getType() != ViewerTagType.MANUAL) {
                throw new IllegalArgumentException("仅可粘贴手动标签");
            }
        }

        List<ViewerTagMemberEntity> existing = viewerTagMemberRepository.findByUsername(memberUsername);
        Set<Long> existingTagIds = new HashSet<>();
        for (ViewerTagMemberEntity m : existing) {
            existingTagIds.add(m.getTagId());
        }
        Map<Long, ViewerTagEntity> existingTags = loadActiveTags(new ArrayList<>(existingTagIds));

        for (ViewerTagMemberEntity m : existing) {
            ViewerTagEntity tag = existingTags.get(m.getTagId());
            if (tag != null && tag.getType() == ViewerTagType.MANUAL) {
                viewerTagMemberRepository.delete(m);
            }
        }

        LocalDateTime now = LocalDateTime.now();
        for (Long tagId : requested) {
            ViewerTagMemberEntity row = new ViewerTagMemberEntity();
            row.setTagId(tagId);
            row.setUsername(memberUsername);
            row.setCreatedTime(now);
            viewerTagMemberRepository.save(row);
        }

        List<TagRef> all = resolveTags(viewerTagMemberRepository.findByUsername(memberUsername));
        UpdateViewerTagsResponse res = new UpdateViewerTagsResponse();
        res.setUsername(memberUsername);
        for (TagRef ref : all) {
            res.getTagIds().add(ref.tagId());
            res.getTagLabels().add(ref.name());
        }
        return res;
    }

    /**
     * 本轮消费恒 0：min&gt;0 或 max&lt;0 则无命中。
     */
    static boolean placeholderFilterMiss(
            Double amountMin, Double amountMax, Integer countMin, Integer countMax) {
        if (amountMin != null && amountMin > 0) {
            return true;
        }
        if (amountMax != null && amountMax < 0) {
            return true;
        }
        if (countMin != null && countMin > 0) {
            return true;
        }
        if (countMax != null && countMax < 0) {
            return true;
        }
        return false;
    }

    private static Sort resolveSort(String sort, String order) {
        String key = sort == null || sort.isBlank() ? "createdTime" : sort.trim();
        if (!SORT_KEYS.contains(key)) {
            throw new IllegalArgumentException("sort 不支持: " + key);
        }
        boolean asc = order != null && "asc".equalsIgnoreCase(order.trim());
        if (order != null && !order.isBlank()) {
            String o = order.trim().toLowerCase(Locale.ROOT);
            if (!"asc".equals(o) && !"desc".equals(o)) {
                throw new IllegalArgumentException("order 仅支持 asc 或 desc");
            }
        }
        // 占位排序列库无对应字段：统一退回 createdTime，避免 JPA 报错
        String property =
                switch (key) {
                    case "lastVisitTime" -> "lastVisitTime";
                    case "consumeCount",
                            "consumeAmount",
                            "lastConsumeTime",
                            "totalLearnDuration",
                            "todayLearnDuration",
                            "completedCourseCount",
                            "firstPayTime" -> "createdTime";
                    default -> "createdTime";
                };
        Sort.Direction dir = asc ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, property).and(Sort.by(Sort.Direction.DESC, "id"));
    }

    private Map<String, List<TagRef>> loadTagsByViewer(List<ViewerEntity> viewers) {
        if (viewers.isEmpty()) {
            return Map.of();
        }
        List<String> viewerUsernames = viewers.stream()
                .map(ViewerEntity::getUsername)
                .filter(java.util.Objects::nonNull)
                .toList();
        List<ViewerTagMemberEntity> members = viewerTagMemberRepository.findByUsernameIn(viewerUsernames);
        if (members.isEmpty()) {
            return Map.of();
        }
        Map<String, List<TagRef>> grouped = new HashMap<>();
        for (TagRefWithViewer ref : resolveTagsWithViewer(members)) {
            grouped.computeIfAbsent(ref.username(), k -> new ArrayList<>())
                    .add(new TagRef(ref.tagId(), ref.name()));
        }
        return grouped;
    }

    private List<TagRef> resolveTags(List<ViewerTagMemberEntity> members) {
        List<TagRef> out = new ArrayList<>();
        for (TagRefWithViewer ref : resolveTagsWithViewer(members)) {
            out.add(new TagRef(ref.tagId(), ref.name()));
        }
        return out;
    }

    private List<TagRefWithViewer> resolveTagsWithViewer(List<ViewerTagMemberEntity> members) {
        if (members.isEmpty()) {
            return List.of();
        }
        Set<Long> tagIds = new HashSet<>();
        for (ViewerTagMemberEntity m : members) {
            tagIds.add(m.getTagId());
        }
        Map<Long, ViewerTagEntity> tags = loadActiveTags(new ArrayList<>(tagIds));
        List<TagRefWithViewer> out = new ArrayList<>();
        for (ViewerTagMemberEntity m : members) {
            ViewerTagEntity tag = tags.get(m.getTagId());
            if (tag == null) {
                continue;
            }
            out.add(new TagRefWithViewer(m.getUsername(), tag.getId(), tag.getName()));
        }
        return out;
    }

    private Map<Long, ViewerTagEntity> loadActiveTags(List<Long> tagIds) {
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

    private void requirePage(int page, int pageSize) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
    }

    /**
     * 学习时长占位过滤。成对参数；{@code gt} → 应空表；{@code lte} → 不挡。
     *
     * @return true 表示直接返回空列表
     */
    static boolean learnDurationFilterMiss(String opRaw, Integer sec) {
        boolean opBlank = opRaw == null || opRaw.isBlank();
        boolean secNull = sec == null;
        if (opBlank && secNull) {
            return false;
        }
        if (opBlank || secNull) {
            throw new IllegalArgumentException("learnDurationOp 与 learnDurationSec 须同时传");
        }
        if (sec < 0) {
            throw new IllegalArgumentException("learnDurationSec 不能为负");
        }
        String op = opRaw.trim().toLowerCase(Locale.ROOT);
        if (!LEARN_DURATION_OPS.contains(op)) {
            throw new IllegalArgumentException("learnDurationOp 仅支持 gt / lte");
        }
        return "gt".equals(op);
    }

    private Specification<ViewerEntity> viewerKeywordTagSpec(
            String type, String kw, List<Long> filterTagIds, String match) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (!kw.isEmpty()) {
                switch (type) {
                    case "id" -> preds.add(cb.equal(root.get("username"), kw));
                    case "phone" -> preds.add(cb.like(root.get("phone"), "%" + kw + "%"));
                    default -> preds.add(cb.like(root.get("nickname"), "%" + kw + "%"));
                }
            }
            if (!filterTagIds.isEmpty() && query != null) {
                if ("all".equals(match)) {
                    for (Long tagId : filterTagIds) {
                        preds.add(existsMember(root, query, cb, List.of(tagId)));
                    }
                } else if ("none".equals(match)) {
                    preds.add(cb.not(existsMember(root, query, cb, filterTagIds)));
                } else {
                    preds.add(existsMember(root, query, cb, filterTagIds));
                }
            }
            if (preds.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(preds.toArray(Predicate[]::new));
        };
    }

    private static Sort resolveLearningSort(String sort, String order) {
        String key = sort == null || sort.isBlank() ? "id" : sort.trim();
        if (!LEARNING_SORT_KEYS.contains(key)) {
            throw new IllegalArgumentException("sort 不支持: " + key);
        }
        boolean asc = order != null && "asc".equalsIgnoreCase(order.trim());
        if (order != null && !order.isBlank()) {
            String o = order.trim().toLowerCase(Locale.ROOT);
            if (!"asc".equals(o) && !"desc".equals(o)) {
                throw new IllegalArgumentException("order 仅支持 asc 或 desc");
            }
        }
        // 占位指标列库无字段，统一按 id 排
        return asc ? Sort.by(Sort.Direction.ASC, "id") : Sort.by(Sort.Direction.DESC, "id");
    }

    private static LearningSummaryListItem toLearningSummaryItem(ViewerEntity viewer, List<TagRef> tags) {
        LearningSummaryListItem item = new LearningSummaryListItem();
        if (viewer.getUsername() != null) {
            item.setUsername(viewer.getUsername());
        }
        item.setNickname(displayNickname(viewer));
        if (viewer.getAvatarUrl() != null && !viewer.getAvatarUrl().isBlank()) {
            item.setAvatarUrl(viewer.getAvatarUrl().trim());
        }
        item.setTotalLearnDurationSec(0);
        item.setTotalLearnDays(0);
        item.setViewedCourseCount(0);
        item.setCompletedCourseCount(0);
        List<Long> tagIds = new ArrayList<>();
        List<String> tagLabels = new ArrayList<>();
        for (TagRef ref : tags) {
            tagIds.add(ref.tagId());
            tagLabels.add(ref.name());
        }
        item.setTagIds(tagIds);
        item.setTagLabels(tagLabels);
        return item;
    }

    private static LearningSummaryListResponse emptyLearningSummary(int page, int pageSize) {
        LearningSummaryListResponse response = new LearningSummaryListResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(0);
        return response;
    }

    private static LearningCourseListResponse emptyLearningCourses(int page, int pageSize) {
        LearningCourseListResponse response = new LearningCourseListResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(0);
        return response;
    }

    private static ViewerListItem toItem(ViewerEntity viewer, List<TagRef> tags) {
        ViewerListItem item = new ViewerListItem();
        if (viewer.getUsername() != null) {
            item.setUsername(viewer.getUsername());
        }
        item.setNickname(displayNickname(viewer));
        if (viewer.getAvatarUrl() != null && !viewer.getAvatarUrl().isBlank()) {
            item.setAvatarUrl(viewer.getAvatarUrl().trim());
        }
        item.setRegisterChannel(viewer.getRegisterChannel());
        String masked = maskPhone(viewer.getPhone());
        if (masked != null) {
            item.setPhoneMasked(masked);
        }
        item.setStatus(viewer.getStatus());
        item.setBanComment(viewer.getBanComment());
        item.setBanPurchase(viewer.getBanPurchase());
        item.setBanAccess(viewer.getBanAccess());
        if (viewer.getCreatedTime() != null) {
            item.setCreatedTime(TIME_FMT.format(viewer.getCreatedTime()));
        }
        if (viewer.getLastVisitTime() != null) {
            item.setLastVisitTime(TIME_FMT.format(viewer.getLastVisitTime()));
        }
        item.setConsumeCount(0);
        item.setConsumeAmount("0.00");
        item.setTotalLearnDurationSec(0);
        item.setTodayLearnDurationSec(0);
        item.setCompletedCourseCount(0);
        List<Long> tagIds = new ArrayList<>();
        List<String> tagLabels = new ArrayList<>();
        for (TagRef ref : tags) {
            tagIds.add(ref.tagId());
            tagLabels.add(ref.name());
        }
        item.setTagIds(tagIds);
        item.setTagLabels(tagLabels);
        return item;
    }

    /** @param username 用户中心 username（业务用户 id） */
    private ViewerEntity requireViewer(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("观众不存在");
        }
        return viewerRepository
                .findByUsername(username.trim())
                .orElseThrow(() -> new IllegalArgumentException("观众不存在"));
    }

    private static void requireFlag(int value, String field) {
        if (value != 0 && value != 1) {
            throw new IllegalArgumentException(field + " 仅支持 0 或 1");
        }
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

    /** 筛选用 tagIds：去重、去掉非法；空=不过滤。 */
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
            long v = Long.parseLong(raw);
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<Long> normalizeTagIds(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        LinkedHashMap<Long, Boolean> uniq = new LinkedHashMap<>();
        for (Long id : tagIds) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("tagIds 含非法 id");
            }
            uniq.put(id, Boolean.TRUE);
        }
        return new ArrayList<>(uniq.keySet());
    }

    /**
     * 列表展示昵称：委托 live-common {@link com.zhibo.viewer.ViewerDisplayNames}
     *（有昵称→昵称；无昵称有手机→「用户{尾号 4 位}」；都没有→「用户_{id}」）。
     */
    public static String displayNickname(ViewerEntity viewer) {
        return ViewerDisplayNames.displayNickname(viewer);
    }

    /** 与 live-service ViewerPhoneMask 同规则。 */
    public static String maskPhone(String phone) {
        if (phone == null) {
            return null;
        }
        String value = phone.trim();
        int length = value.length();
        if (length == 0) {
            return null;
        }
        if (length <= 4) {
            return "****";
        }
        if (length == 11) {
            return value.substring(0, 3) + "****" + value.substring(length - 4);
        }
        return value.substring(0, 2) + "****" + value.substring(length - 2);
    }

    private static ViewerListResponse emptyPage(int page, int pageSize) {
        ViewerListResponse response = new ViewerListResponse();
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotal(0);
        return response;
    }

    private record TagRef(long tagId, String name) {}

    private record TagRefWithViewer(String username, long tagId, String name) {}
}
