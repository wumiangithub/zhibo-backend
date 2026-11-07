package com.zhibo.admin.course;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.course.dto.BindLivesRequest;
import com.zhibo.admin.course.dto.BindMaterialsRequest;
import com.zhibo.admin.course.dto.CatalogListResponse;
import com.zhibo.admin.course.dto.CatalogNodeView;
import com.zhibo.admin.course.dto.CatalogUsedContentsResponse;
import com.zhibo.admin.course.dto.CreateChapterRequest;
import com.zhibo.admin.course.dto.CreateSubcourseRequest;
import com.zhibo.admin.course.dto.CatalogNodeIdResponse;
import com.zhibo.admin.course.dto.DeleteChapterRequest;
import com.zhibo.admin.course.dto.DeleteSubcourseRequest;
import com.zhibo.admin.course.dto.ReorderCatalogItem;
import com.zhibo.admin.course.dto.ReorderCatalogRequest;
import com.zhibo.admin.course.dto.ReorderSubcoursesRequest;
import com.zhibo.admin.course.dto.UpdateChapterRequest;
import com.zhibo.admin.course.dto.UpdateLessonRequest;
import com.zhibo.admin.course.dto.UpdateSubcourseRequest;
import com.zhibo.common.UuidIds;
import com.zhibo.course.CatalogSiblingOrder;
import com.zhibo.course.CourseCatalogContentKind;
import com.zhibo.course.CourseCatalogNodeEntity;
import com.zhibo.course.CourseCatalogNodeRepository;
import com.zhibo.course.CourseCatalogNodeType;
import com.zhibo.course.CourseEntity;
import com.zhibo.course.CourseRepository;
import com.zhibo.material.MaterialAudit;
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CourseCatalogService {

    static final long COVER_MAX_BYTES = 5L * 1024 * 1024;
    static final int TITLE_LIMIT = 30;

    private final CourseRepository courseRepository;
    private final CourseCatalogNodeRepository catalogRepository;
    private final ActivityRepository activityRepository;
    private final MaterialRepository materialRepository;

    public CatalogListResponse list(String courseId) {
        CourseEntity course = requireCourse(courseId);
        List<CourseCatalogNodeEntity> nodes = CatalogSiblingOrder.orderFlat(
                catalogRepository.findActiveByCourseId(course.getId()));
        Map<Long, Integer> chapterCountBySub = new HashMap<>();
        Map<Long, Integer> lessonCountBySub = new HashMap<>();
        Map<Long, Long> parentOf = new HashMap<>();
        for (CourseCatalogNodeEntity n : nodes) {
            parentOf.put(n.getId(), n.getParentId());
        }
        for (CourseCatalogNodeEntity n : nodes) {
            if (n.getNodeType() == CourseCatalogNodeType.CHAPTER && n.getParentId() > 0) {
                chapterCountBySub.merge(n.getParentId(), 1, Integer::sum);
            }
            if (n.getNodeType() == CourseCatalogNodeType.LESSON) {
                Long subId = resolveSubcourseAncestor(n.getParentId(), parentOf, nodes);
                if (subId != null) {
                    lessonCountBySub.merge(subId, 1, Integer::sum);
                }
            }
        }
        List<CatalogNodeView> list = new ArrayList<>();
        for (CourseCatalogNodeEntity n : nodes) {
            CatalogNodeView view = toView(n);
            if (n.getNodeType() == CourseCatalogNodeType.SUBCOURSE) {
                view.setChapterCount(chapterCountBySub.getOrDefault(n.getId(), 0));
                view.setLessonCount(lessonCountBySub.getOrDefault(n.getId(), 0));
            }
            list.add(view);
        }
        CatalogListResponse response = new CatalogListResponse();
        response.setList(list);
        return response;
    }

    public CatalogUsedContentsResponse usedContents(String courseId) {
        CourseEntity course = requireCourse(courseId);
        CatalogUsedContentsResponse response = new CatalogUsedContentsResponse();
        response.setLives(catalogRepository.findUsedContentIds(course.getId(), CourseCatalogContentKind.LIVE));
        List<String> materials = new ArrayList<>();
        materials.addAll(catalogRepository.findUsedContentIds(course.getId(), CourseCatalogContentKind.IMAGE));
        materials.addAll(catalogRepository.findUsedContentIds(course.getId(), CourseCatalogContentKind.VIDEO));
        materials.addAll(catalogRepository.findUsedContentIds(course.getId(), CourseCatalogContentKind.DOCUMENT));
        materials.addAll(catalogRepository.findUsedContentIds(course.getId(), CourseCatalogContentKind.ARTICLE));
        response.setMaterials(materials);
        return response;
    }

    @Transactional
    public void bindLives(String courseId, BindLivesRequest request) {
        if (request == null || request.getParentId() == null) {
            throw new IllegalArgumentException("parentId 不能为空");
        }
        if (request.getActivityIds() == null || request.getActivityIds().isEmpty()) {
            throw new IllegalArgumentException("请选择直播");
        }
        CourseEntity course = requireCourse(courseId);
        long parentId = request.getParentId();
        assertCanAttachLessonOrChapter(course.getId(), parentId, true);

        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String raw : request.getActivityIds()) {
            String id = UuidIds.require(raw, "直播 id 无效", "直播 id 须为本站 UUID");
            ids.add(id);
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("请选择直播");
        }

        List<CourseCatalogNodeEntity> siblings = catalogRepository.findActiveByCourseIdAndParentId(
                course.getId(), parentId);
        int sort = CatalogSiblingOrder.nextSortOrder(siblings);
        boolean bump = sort > 0;
        for (String activityId : ids) {
            if (catalogRepository.existsActiveContent(
                    course.getId(), CourseCatalogContentKind.LIVE, activityId)) {
                throw new IllegalArgumentException("该直播已关联到本课程，不可重复添加");
            }
            ActivityEntity activity = activityRepository.findById(activityId)
                    .filter(a -> a.getDeletedTime() == null)
                    .orElseThrow(() -> new IllegalArgumentException("直播不存在"));
            String title = trimTitle(activity.getTitle(), "直播");
            CourseCatalogNodeEntity node = new CourseCatalogNodeEntity();
            node.setCourseId(course.getId());
            node.setParentId(parentId);
            node.setNodeType(CourseCatalogNodeType.LESSON);
            node.setTitle(title);
            node.setContentKind(CourseCatalogContentKind.LIVE);
            node.setContentId(activityId);
            node.setSortOrder(sort);
            catalogRepository.save(node);
            if (bump) {
                sort++;
            }
        }
        syncContentCount(course);
    }

    @Transactional
    public void bindMaterials(String courseId, BindMaterialsRequest request) {
        if (request == null || request.getParentId() == null) {
            throw new IllegalArgumentException("parentId 不能为空");
        }
        if (request.getMaterialIds() == null || request.getMaterialIds().isEmpty()) {
            throw new IllegalArgumentException("请选择素材");
        }
        CourseEntity course = requireCourse(courseId);
        long parentId = request.getParentId();
        assertCanAttachLessonOrChapter(course.getId(), parentId, true);

        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (String raw : request.getMaterialIds()) {
            String id = UuidIds.require(raw, "素材 id 无效", "素材 id 须为本站 UUID");
            ids.add(id);
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("请选择素材");
        }

        List<CourseCatalogNodeEntity> siblings = catalogRepository.findActiveByCourseIdAndParentId(
                course.getId(), parentId);
        int sort = CatalogSiblingOrder.nextSortOrder(siblings);
        boolean bump = sort > 0;
        for (String materialId : ids) {
            MaterialEntity material = materialRepository.findById(materialId)
                    .filter(m -> m.getDeletedTime() == null)
                    .orElseThrow(() -> new IllegalArgumentException("素材不存在"));
            int contentKind = contentKindOfMaterialType(material.getType());
            if (materialAlreadyUsed(course.getId(), materialId)) {
                throw new IllegalArgumentException("该素材已关联到本课程，不可重复添加");
            }
            String title = trimTitle(material.getName(), "素材");
            CourseCatalogNodeEntity node = new CourseCatalogNodeEntity();
            node.setCourseId(course.getId());
            node.setParentId(parentId);
            node.setNodeType(CourseCatalogNodeType.LESSON);
            node.setTitle(title);
            node.setContentKind(contentKind);
            node.setContentId(materialId);
            node.setSortOrder(sort);
            catalogRepository.save(node);
            if (bump) {
                sort++;
            }
        }
        syncContentCount(course);
    }

    @Transactional
    public CatalogNodeView createChapter(String courseId, CreateChapterRequest request) {
        if (request == null || request.getParentId() == null) {
            throw new IllegalArgumentException("parentId 不能为空");
        }
        CourseEntity course = requireCourse(courseId);
        long parentId = request.getParentId();
        assertCanAttachLessonOrChapter(course.getId(), parentId, false);
        String title = requireNodeTitle(request.getTitle(), "请输入章节标题");

        List<CourseCatalogNodeEntity> siblings = catalogRepository.findActiveByCourseIdAndParentId(
                course.getId(), parentId);
        CourseCatalogNodeEntity node = new CourseCatalogNodeEntity();
        node.setCourseId(course.getId());
        node.setParentId(parentId);
        node.setNodeType(CourseCatalogNodeType.CHAPTER);
        node.setTitle(title);
        node.setSortOrder(CatalogSiblingOrder.nextSortOrder(siblings));
        return toView(catalogRepository.save(node));
    }

    @Transactional
    public CatalogNodeView updateChapter(String courseId, UpdateChapterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (request.getId() == null) {
            throw new IllegalArgumentException("章节 id 不能为空");
        }
        CourseEntity course = requireCourse(courseId);
        CourseCatalogNodeEntity node = catalogRepository
                .findByIdAndCourseIdAndDeletedTimeIsNull(request.getId(), course.getId())
                .orElseThrow(() -> new IllegalArgumentException("章节不存在"));
        if (node.getNodeType() != CourseCatalogNodeType.CHAPTER) {
            throw new IllegalArgumentException("不是章节节点");
        }
        String title = requireNodeTitle(request.getTitle(), "请输入章节标题");
        node.setTitle(title);
        return toView(catalogRepository.save(node));
    }

    @Transactional
    public CatalogNodeView updateLesson(String courseId, UpdateLessonRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (request.getId() == null) {
            throw new IllegalArgumentException("小节 id 不能为空");
        }
        CourseEntity course = requireCourse(courseId);
        CourseCatalogNodeEntity node = catalogRepository
                .findByIdAndCourseIdAndDeletedTimeIsNull(request.getId(), course.getId())
                .orElseThrow(() -> new IllegalArgumentException("小节不存在"));
        if (node.getNodeType() != CourseCatalogNodeType.LESSON) {
            throw new IllegalArgumentException("不是小节节点");
        }
        String title = requireNodeTitle(request.getTitle(), "请输入小节标题");
        node.setTitle(title);
        return toView(catalogRepository.save(node));
    }

    @Transactional
    public CatalogNodeIdResponse deleteChapter(String courseId, DeleteChapterRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (request.getId() == null) {
            throw new IllegalArgumentException("章节 id 不能为空");
        }
        if (request.getKeepContents() == null) {
            throw new IllegalArgumentException("请选择删除方式");
        }
        CourseEntity course = requireCourse(courseId);
        CourseCatalogNodeEntity chapter = catalogRepository
                .findByIdAndCourseIdAndDeletedTimeIsNull(request.getId(), course.getId())
                .orElseThrow(() -> new IllegalArgumentException("章节不存在"));
        if (chapter.getNodeType() != CourseCatalogNodeType.CHAPTER) {
            throw new IllegalArgumentException("不是章节节点");
        }

        LocalDateTime now = LocalDateTime.now();

        if (Boolean.TRUE.equals(request.getKeepContents())) {
            Long targetId = request.getTargetChapterId();
            if (targetId != null) {
                if (targetId.equals(chapter.getId())) {
                    throw new IllegalArgumentException("不能移动到正在删除的章节");
                }
                CourseCatalogNodeEntity target = catalogRepository
                        .findByIdAndCourseIdAndDeletedTimeIsNull(targetId, course.getId())
                        .orElseThrow(() -> new IllegalArgumentException("目标章节不存在"));
                if (target.getNodeType() != CourseCatalogNodeType.CHAPTER) {
                    throw new IllegalArgumentException("目标不是章节");
                }
                if (!Objects.equals(target.getParentId(), chapter.getParentId())) {
                    throw new IllegalArgumentException("目标章节须与删除章节同级");
                }
                List<CourseCatalogNodeEntity> children = catalogRepository.findActiveByCourseIdAndParentId(
                        course.getId(), chapter.getId());
                for (CourseCatalogNodeEntity child : children) {
                    if (child.getNodeType() == CourseCatalogNodeType.LESSON) {
                        child.setParentId(targetId);
                        catalogRepository.save(child);
                    }
                }
            }
            // 无 target：仅软删章节；有 target：已迁直属小节，再软删本节点
            chapter.setDeletedTime(now);
            catalogRepository.save(chapter);
        } else {
            List<CourseCatalogNodeEntity> children = catalogRepository.findActiveByCourseIdAndParentId(
                    course.getId(), chapter.getId());
            for (CourseCatalogNodeEntity child : children) {
                if (child.getNodeType() == CourseCatalogNodeType.LESSON) {
                    child.setDeletedTime(now);
                    catalogRepository.save(child);
                }
            }
            chapter.setDeletedTime(now);
            catalogRepository.save(chapter);
            syncContentCount(course);
        }

        return new CatalogNodeIdResponse(chapter.getId());
    }

    @Transactional
    public CatalogNodeView createSubcourse(String courseId, CreateSubcourseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        CourseEntity course = requireCourse(courseId);
        String title = requireNodeTitle(request.getTitle(), "请输入子课程名称");
        MaterialEntity cover = requireCover(request.getCoverMaterialId(), "请选择子课程封面");

        long existingSubs = catalogRepository.countActiveByCourseIdAndNodeType(
                course.getId(), CourseCatalogNodeType.SUBCOURSE);
        boolean first = existingSubs == 0;
        long rootContent = catalogRepository.countActiveRootByCourseIdAndNodeTypes(
                course.getId(),
                List.of(CourseCatalogNodeType.CHAPTER, CourseCatalogNodeType.LESSON));
        if (first && rootContent > 0 && !Boolean.TRUE.equals(request.getMoveRootContents())) {
            throw new IllegalArgumentException("请确认将已有内容迁入该子课程");
        }

        List<CourseCatalogNodeEntity> rootSubs = catalogRepository.findActiveByCourseIdAndParentId(
                        course.getId(), 0L)
                .stream()
                .filter(n -> n.getNodeType() == CourseCatalogNodeType.SUBCOURSE)
                .toList();
        CourseCatalogNodeEntity node = new CourseCatalogNodeEntity();
        node.setCourseId(course.getId());
        node.setParentId(0L);
        node.setNodeType(CourseCatalogNodeType.SUBCOURSE);
        node.setTitle(title);
        node.setCoverMaterialId(cover.getId());
        node.setCoverUrl(cover.getUrl().trim());
        node.setSortOrder(CatalogSiblingOrder.nextSortOrder(rootSubs));
        CourseCatalogNodeEntity saved = catalogRepository.save(node);

        if (first && rootContent > 0) {
            catalogRepository.reparentRootNodes(
                    course.getId(),
                    saved.getId(),
                    List.of(CourseCatalogNodeType.CHAPTER, CourseCatalogNodeType.LESSON));
        }

        CatalogNodeView view = toView(saved);
        List<CourseCatalogNodeEntity> all = CatalogSiblingOrder.orderFlat(
                catalogRepository.findActiveByCourseId(course.getId()));
        Map<Long, Long> parentOf = new HashMap<>();
        for (CourseCatalogNodeEntity n : all) {
            parentOf.put(n.getId(), n.getParentId());
        }
        int chapters = 0;
        int lessons = 0;
        for (CourseCatalogNodeEntity n : all) {
            if (n.getParentId() == saved.getId() && n.getNodeType() == CourseCatalogNodeType.CHAPTER) {
                chapters++;
            }
            if (n.getNodeType() == CourseCatalogNodeType.LESSON) {
                Long sub = resolveSubcourseAncestor(n.getParentId(), parentOf, all);
                if (Objects.equals(sub, saved.getId())) {
                    lessons++;
                }
            }
        }
        view.setChapterCount(chapters);
        view.setLessonCount(lessons);
        return view;
    }

    @Transactional
    public CatalogNodeView updateSubcourse(String courseId, UpdateSubcourseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (request.getId() == null) {
            throw new IllegalArgumentException("子课程 id 不能为空");
        }
        CourseEntity course = requireCourse(courseId);
        CourseCatalogNodeEntity node = catalogRepository
                .findByIdAndCourseIdAndDeletedTimeIsNull(request.getId(), course.getId())
                .orElseThrow(() -> new IllegalArgumentException("子课程不存在"));
        if (node.getNodeType() != CourseCatalogNodeType.SUBCOURSE) {
            throw new IllegalArgumentException("不是子课程节点");
        }
        String title = requireNodeTitle(request.getTitle(), "请输入子课程名称");
        MaterialEntity cover = requireCover(request.getCoverMaterialId(), "请选择子课程封面");
        node.setTitle(title);
        node.setCoverMaterialId(cover.getId());
        node.setCoverUrl(cover.getUrl().trim());
        return toView(catalogRepository.save(node));
    }

    @Transactional
    public CatalogNodeIdResponse deleteSubcourse(String courseId, DeleteSubcourseRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        if (request.getId() == null) {
            throw new IllegalArgumentException("子课程 id 不能为空");
        }
        if (request.getKeepContents() == null) {
            throw new IllegalArgumentException("请选择删除方式");
        }
        CourseEntity course = requireCourse(courseId);
        CourseCatalogNodeEntity sub = catalogRepository
                .findByIdAndCourseIdAndDeletedTimeIsNull(request.getId(), course.getId())
                .orElseThrow(() -> new IllegalArgumentException("子课程不存在"));
        if (sub.getNodeType() != CourseCatalogNodeType.SUBCOURSE) {
            throw new IllegalArgumentException("不是子课程节点");
        }

        LocalDateTime now = LocalDateTime.now();

        if (Boolean.TRUE.equals(request.getKeepContents())) {
            Long targetId = request.getTargetSubcourseId();
            if (targetId != null) {
                if (targetId.equals(sub.getId())) {
                    throw new IllegalArgumentException("不能移动到正在删除的子课程");
                }
                CourseCatalogNodeEntity target = catalogRepository
                        .findByIdAndCourseIdAndDeletedTimeIsNull(targetId, course.getId())
                        .orElseThrow(() -> new IllegalArgumentException("目标子课程不存在"));
                if (target.getNodeType() != CourseCatalogNodeType.SUBCOURSE) {
                    throw new IllegalArgumentException("目标不是子课程");
                }
                List<CourseCatalogNodeEntity> children = catalogRepository.findActiveByCourseIdAndParentId(
                        course.getId(), sub.getId());
                for (CourseCatalogNodeEntity child : children) {
                    if (child.getNodeType() == CourseCatalogNodeType.CHAPTER
                            || child.getNodeType() == CourseCatalogNodeType.LESSON) {
                        child.setParentId(targetId);
                        catalogRepository.save(child);
                    }
                }
            }
            // 无 target：仅软删子课程；有 target：已迁直属章节/根小节，再软删本节点
            sub.setDeletedTime(now);
            catalogRepository.save(sub);
        } else {
            List<CourseCatalogNodeEntity> children = catalogRepository.findActiveByCourseIdAndParentId(
                    course.getId(), sub.getId());
            for (CourseCatalogNodeEntity child : children) {
                if (child.getNodeType() == CourseCatalogNodeType.CHAPTER) {
                    List<CourseCatalogNodeEntity> lessons =
                            catalogRepository.findActiveByCourseIdAndParentId(
                                    course.getId(), child.getId());
                    for (CourseCatalogNodeEntity lesson : lessons) {
                        lesson.setDeletedTime(now);
                        catalogRepository.save(lesson);
                    }
                }
                child.setDeletedTime(now);
                catalogRepository.save(child);
            }
            sub.setDeletedTime(now);
            catalogRepository.save(sub);
            syncContentCount(course);
        }

        return new CatalogNodeIdResponse(sub.getId());
    }

    @Transactional
    public void reorderSubcourses(String courseId, ReorderSubcoursesRequest request) {
        if (request == null || request.getIds() == null || request.getIds().isEmpty()) {
            throw new IllegalArgumentException("请提交子课程排序");
        }
        CourseEntity course = requireCourse(courseId);
        List<Long> ids = request.getIds();
        LinkedHashSet<Long> ordered = new LinkedHashSet<>();
        for (Long id : ids) {
            if (id == null) {
                throw new IllegalArgumentException("子课程 id 无效");
            }
            if (!ordered.add(id)) {
                throw new IllegalArgumentException("子课程 id 重复");
            }
        }
        List<CourseCatalogNodeEntity> existing = catalogRepository.findActiveByCourseId(course.getId())
                .stream()
                .filter(n -> n.getNodeType() == CourseCatalogNodeType.SUBCOURSE)
                .toList();
        if (existing.size() != ordered.size()) {
            throw new IllegalArgumentException("子课程数量与当前不一致，请刷新后重试");
        }
        Map<Long, CourseCatalogNodeEntity> byId = new HashMap<>();
        for (CourseCatalogNodeEntity n : existing) {
            byId.put(n.getId(), n);
        }
        for (Long id : ordered) {
            if (!byId.containsKey(id)) {
                throw new IllegalArgumentException("子课程不存在或不属于本课");
            }
        }
        List<CourseCatalogNodeEntity> inOrder = new ArrayList<>();
        for (Long id : ordered) {
            inOrder.add(byId.get(id));
        }
        CatalogSiblingOrder.applyExplicitOrder(inOrder);
        for (CourseCatalogNodeEntity n : inOrder) {
            catalogRepository.save(n);
        }
    }

    /**
     * 目录排序弹窗统一保存。{@code mode=chapters} 只重排上下文下直挂章节+小节；
     * {@code mode=lessons} 可改小节 parentId（挂到章节或回挂上下文），并重写受影响兄弟组的 sortOrder（1..n）。
     */
    @Transactional
    public void reorderCatalog(String courseId, ReorderCatalogRequest request) {
        if (request == null || request.getParentId() == null
                || request.getItems() == null || request.getItems().isEmpty()) {
            throw new IllegalArgumentException("请提交排序");
        }
        String mode = request.getMode() == null ? "" : request.getMode().trim().toLowerCase(Locale.ROOT);
        if (!"chapters".equals(mode) && !"lessons".equals(mode)) {
            throw new IllegalArgumentException("排序模式无效");
        }
        CourseEntity course = requireCourse(courseId);
        long contextParentId = request.getParentId();
        assertCatalogContextParent(course.getId(), contextParentId);

        LinkedHashSet<Long> seen = new LinkedHashSet<>();
        List<ReorderCatalogItem> items = request.getItems();
        for (ReorderCatalogItem item : items) {
            if (item == null || item.getId() == null || item.getParentId() == null) {
                throw new IllegalArgumentException("排序项无效");
            }
            if (!seen.add(item.getId())) {
                throw new IllegalArgumentException("排序项 id 重复");
            }
        }

        List<CourseCatalogNodeEntity> all = catalogRepository.findActiveByCourseId(course.getId());
        Map<Long, CourseCatalogNodeEntity> byId = new HashMap<>();
        for (CourseCatalogNodeEntity n : all) {
            byId.put(n.getId(), n);
        }

        if ("chapters".equals(mode)) {
            reorderCatalogChaptersMode(contextParentId, items, byId, all);
        } else {
            reorderCatalogLessonsMode(contextParentId, items, byId, all);
        }
    }

    private void reorderCatalogChaptersMode(
            long contextParentId,
            List<ReorderCatalogItem> items,
            Map<Long, CourseCatalogNodeEntity> byId,
            List<CourseCatalogNodeEntity> all) {
        // 章排序只含章节，不含任何小节
        List<CourseCatalogNodeEntity> expected = all.stream()
                .filter(n -> n.getParentId() == contextParentId)
                .filter(n -> n.getNodeType() == CourseCatalogNodeType.CHAPTER)
                .toList();
        List<Long> expectedIds = expected.stream().map(CourseCatalogNodeEntity::getId).toList();
        List<Long> gotIds = items.stream().map(ReorderCatalogItem::getId).toList();
        if (!CatalogSiblingOrder.sameIdSet(expectedIds, gotIds)) {
            throw new IllegalArgumentException("章排序内容与当前不一致，请刷新后重试");
        }
        List<CourseCatalogNodeEntity> ordered = new ArrayList<>();
        for (ReorderCatalogItem item : items) {
            if (item.getParentId() != contextParentId) {
                throw new IllegalArgumentException("章排序不可改父节点");
            }
            CourseCatalogNodeEntity n = byId.get(item.getId());
            if (n == null) {
                throw new IllegalArgumentException("节点不存在");
            }
            if (n.getNodeType() != CourseCatalogNodeType.CHAPTER) {
                throw new IllegalArgumentException("章排序仅支持章节");
            }
            if (n.getParentId() != contextParentId) {
                throw new IllegalArgumentException("节点不属于当前目录");
            }
            ordered.add(n);
        }
        CatalogSiblingOrder.applyExplicitOrder(ordered);
        for (CourseCatalogNodeEntity n : ordered) {
            catalogRepository.save(n);
        }
    }

    private void reorderCatalogLessonsMode(
            long contextParentId,
            List<ReorderCatalogItem> items,
            Map<Long, CourseCatalogNodeEntity> byId,
            List<CourseCatalogNodeEntity> all) {
        Set<Long> chapterIdsUnderContext = new HashSet<>();
        for (CourseCatalogNodeEntity n : all) {
            if (n.getParentId() == contextParentId && n.getNodeType() == CourseCatalogNodeType.CHAPTER) {
                chapterIdsUnderContext.add(n.getId());
            }
        }
        Set<Long> expectedLessonIds = new HashSet<>();
        for (CourseCatalogNodeEntity n : all) {
            if (n.getNodeType() != CourseCatalogNodeType.LESSON) {
                continue;
            }
            if (n.getParentId() == contextParentId
                    || chapterIdsUnderContext.contains(n.getParentId())) {
                expectedLessonIds.add(n.getId());
            }
        }

        Set<Long> gotChapterIds = new HashSet<>();
        Set<Long> gotLessonIds = new HashSet<>();
        Long currentChapter = null;
        List<CourseCatalogNodeEntity> contextOrdered = new ArrayList<>();
        Map<Long, List<CourseCatalogNodeEntity>> lessonsByChapter = new HashMap<>();

        for (ReorderCatalogItem item : items) {
            CourseCatalogNodeEntity n = byId.get(item.getId());
            if (n == null) {
                throw new IllegalArgumentException("节点不存在或不属于本课");
            }
            long targetParent = item.getParentId();
            if (n.getNodeType() == CourseCatalogNodeType.CHAPTER) {
                if (targetParent != contextParentId) {
                    throw new IllegalArgumentException("章节不可改挂到其它父节点");
                }
                if (!chapterIdsUnderContext.contains(n.getId())) {
                    throw new IllegalArgumentException("章节不属于当前目录");
                }
                gotChapterIds.add(n.getId());
                currentChapter = n.getId();
                contextOrdered.add(n);
                lessonsByChapter.putIfAbsent(n.getId(), new ArrayList<>());
                continue;
            }
            if (n.getNodeType() != CourseCatalogNodeType.LESSON) {
                throw new IllegalArgumentException("小节排序仅支持章节与小节");
            }
            if (!expectedLessonIds.contains(n.getId())) {
                throw new IllegalArgumentException("小节不属于当前目录树");
            }
            gotLessonIds.add(n.getId());
            if (targetParent == contextParentId) {
                currentChapter = null;
                n.setParentId(contextParentId);
                contextOrdered.add(n);
            } else if (chapterIdsUnderContext.contains(targetParent)) {
                if (currentChapter == null || !currentChapter.equals(targetParent)) {
                    // 允许章折叠时直接挂入：不要求紧跟在章节行后，但须是本上下文下的章
                    currentChapter = targetParent;
                }
                n.setParentId(targetParent);
                lessonsByChapter.computeIfAbsent(targetParent, k -> new ArrayList<>()).add(n);
            } else {
                throw new IllegalArgumentException("小节只能挂在当前目录或下属章节下");
            }
        }

        if (!gotChapterIds.equals(chapterIdsUnderContext)) {
            throw new IllegalArgumentException("章节集合与当前不一致，请刷新后重试");
        }
        if (!gotLessonIds.equals(expectedLessonIds)) {
            throw new IllegalArgumentException("小节集合与当前不一致，请刷新后重试");
        }

        CatalogSiblingOrder.applyExplicitOrder(contextOrdered);
        for (CourseCatalogNodeEntity n : contextOrdered) {
            catalogRepository.save(n);
        }
        for (Long chapterId : chapterIdsUnderContext) {
            List<CourseCatalogNodeEntity> nested = lessonsByChapter.getOrDefault(chapterId, List.of());
            if (nested.isEmpty()) {
                continue;
            }
            CatalogSiblingOrder.applyExplicitOrder(nested);
            for (CourseCatalogNodeEntity n : nested) {
                catalogRepository.save(n);
            }
        }
    }

    private void assertCatalogContextParent(String courseId, long parentId) {
        if (parentId == 0L) {
            return;
        }
        CourseCatalogNodeEntity parent = catalogRepository
                .findByIdAndCourseIdAndDeletedTimeIsNull(parentId, courseId)
                .orElseThrow(() -> new IllegalArgumentException("父节点不存在"));
        if (parent.getNodeType() != CourseCatalogNodeType.SUBCOURSE) {
            throw new IllegalArgumentException("目录上下文须为课程根或子课程");
        }
    }

    /**
     * @param forLesson true=挂小节（父可为章节或子课程）；false=挂章节（父须为 0 或子课程）
     */
    private void assertCanAttachLessonOrChapter(String courseId, long parentId, boolean forLesson) {
        boolean hasSub = catalogRepository.countActiveByCourseIdAndNodeType(
                courseId, CourseCatalogNodeType.SUBCOURSE) > 0;
        if (parentId == 0L) {
            if (hasSub) {
                throw new IllegalArgumentException("已有子课程时不可在根目录添加");
            }
            return;
        }
        CourseCatalogNodeEntity parent = catalogRepository
                .findByIdAndCourseIdAndDeletedTimeIsNull(parentId, courseId)
                .orElseThrow(() -> new IllegalArgumentException("父节点不存在"));
        if (forLesson) {
            if (parent.getNodeType() == CourseCatalogNodeType.SUBCOURSE) {
                return;
            }
            if (parent.getNodeType() == CourseCatalogNodeType.CHAPTER) {
                if (hasSub && parent.getParentId() == 0L) {
                    throw new IllegalArgumentException("已有子课程时不可挂在根下章节");
                }
                return;
            }
            throw new IllegalArgumentException("小节只能挂在课程根、章节或子课程下");
        }
        if (parent.getNodeType() != CourseCatalogNodeType.SUBCOURSE) {
            throw new IllegalArgumentException("章节只能挂在课程根或子课程下");
        }
    }

    private int syncContentCount(CourseEntity course) {
        long lessons = catalogRepository.countActiveByCourseIdAndNodeType(
                course.getId(), CourseCatalogNodeType.LESSON);
        int count = (int) Math.min(lessons, Integer.MAX_VALUE);
        course.setContentCount(count);
        courseRepository.save(course);
        return count;
    }

    private CourseEntity requireCourse(String id) {
        String uuid = UuidIds.require(id, "课程 id 无效", "课程 id 须为本站 UUID");
        CourseEntity entity = courseRepository.findById(uuid)
                .orElseThrow(() -> new IllegalArgumentException("课程不存在"));
        if (entity.getDeletedTime() != null) {
            throw new IllegalArgumentException("课程不存在");
        }
        return entity;
    }

    private static String requireNodeTitle(String raw, String emptyMsg) {
        String title = raw == null ? "" : raw.trim();
        if (title.isEmpty()) {
            throw new IllegalArgumentException(emptyMsg);
        }
        if (title.length() > TITLE_LIMIT) {
            throw new IllegalArgumentException("名称不能超过" + TITLE_LIMIT + "个字");
        }
        return title;
    }

    private static String trimTitle(String raw, String fallback) {
        String title = raw == null ? "" : raw.trim();
        if (title.isEmpty()) {
            title = fallback;
        }
        if (title.length() > TITLE_LIMIT) {
            return title.substring(0, TITLE_LIMIT);
        }
        return title;
    }

    /** 素材中心 type → 小节 contentKind（2～5）。 */
    private static int contentKindOfMaterialType(String type) {
        String t = type == null ? "" : type.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "image" -> CourseCatalogContentKind.IMAGE;
            case "video" -> CourseCatalogContentKind.VIDEO;
            case "document" -> CourseCatalogContentKind.DOCUMENT;
            case "article" -> CourseCatalogContentKind.ARTICLE;
            default -> throw new IllegalArgumentException("不支持的素材类型");
        };
    }

    /** 同课下任一素材类 contentKind 已占用该 materialId。 */
    private boolean materialAlreadyUsed(String courseId, String materialId) {
        return catalogRepository.existsActiveContent(courseId, CourseCatalogContentKind.IMAGE, materialId)
                || catalogRepository.existsActiveContent(courseId, CourseCatalogContentKind.VIDEO, materialId)
                || catalogRepository.existsActiveContent(courseId, CourseCatalogContentKind.DOCUMENT, materialId)
                || catalogRepository.existsActiveContent(courseId, CourseCatalogContentKind.ARTICLE, materialId);
    }

    private MaterialEntity requireCover(String materialId, String emptyMsg) {
        String id = materialId == null ? "" : materialId.trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException(emptyMsg);
        }
        MaterialEntity material = materialRepository.findById(id)
                .filter(item -> item.getDeletedTime() == null)
                .orElseThrow(() -> new IllegalArgumentException("封面素材不存在"));
        if (!"image".equals(material.getType())) {
            throw new IllegalArgumentException("封面须为图片素材");
        }
        if (!MaterialAudit.bothPassed(material)) {
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

    private static void resolveImageMime(MaterialEntity material) {
        String mime = material.getMimeType();
        if (mime != null) {
            String normalized = mime.toLowerCase(Locale.ROOT);
            if ("image/jpg".equals(normalized) || "image/jpeg".equals(normalized) || "image/png".equals(normalized)) {
                return;
            }
        }
        String name = material.getName() == null ? "" : material.getName().toLowerCase(Locale.ROOT);
        if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
            return;
        }
        throw new IllegalArgumentException("封面只支持 JPG/PNG");
    }

    private CatalogNodeView toView(CourseCatalogNodeEntity n) {
        CatalogNodeView view = new CatalogNodeView();
        view.setId(n.getId());
        view.setCourseId(n.getCourseId());
        view.setParentId(n.getParentId());
        view.setNodeType(n.getNodeType());
        view.setTitle(n.getTitle());
        view.setCoverMaterialId(n.getCoverMaterialId());
        view.setCoverUrl(n.getCoverUrl());
        view.setContentKind(n.getContentKind());
        view.setContentId(n.getContentId());
        view.setSortOrder(n.getSortOrder());
        return view;
    }

    /** 向上找子课程祖先；没有则 null。 */
    private static Long resolveSubcourseAncestor(
            long parentId,
            Map<Long, Long> parentOf,
            List<CourseCatalogNodeEntity> nodes) {
        Map<Long, CourseCatalogNodeEntity> byId = new HashMap<>();
        for (CourseCatalogNodeEntity n : nodes) {
            byId.put(n.getId(), n);
        }
        Set<Long> seen = new HashSet<>();
        long cur = parentId;
        while (cur > 0 && seen.add(cur)) {
            CourseCatalogNodeEntity n = byId.get(cur);
            if (n == null) {
                return null;
            }
            if (n.getNodeType() == CourseCatalogNodeType.SUBCOURSE) {
                return n.getId();
            }
            cur = parentOf.getOrDefault(cur, 0L);
        }
        return null;
    }
}
