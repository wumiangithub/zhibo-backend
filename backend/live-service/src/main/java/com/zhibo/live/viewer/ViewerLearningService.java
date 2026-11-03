package com.zhibo.live.viewer;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.common.UuidIds;
import com.zhibo.course.CatalogSiblingOrder;
import com.zhibo.course.CourseCatalogContentKind;
import com.zhibo.course.CourseCatalogNodeEntity;
import com.zhibo.course.CourseCatalogNodeRepository;
import com.zhibo.course.CourseCatalogNodeType;
import com.zhibo.course.CourseEntitlementSupport;
import com.zhibo.course.CourseEntity;
import com.zhibo.course.CourseLessonParticipateStatus;
import com.zhibo.course.CourseLessonProgressEntity;
import com.zhibo.course.CourseLessonProgressRepository;
import com.zhibo.course.CourseRepository;
import com.zhibo.course.CourseUserEntity;
import com.zhibo.course.CourseUserRepository;
import com.zhibo.live.viewer.dto.ViewerLearningCatalogNode;
import com.zhibo.live.viewer.dto.ViewerLearningCourseDetailResponse;
import com.zhibo.live.viewer.dto.ViewerLearningCourseItem;
import com.zhibo.live.viewer.dto.ViewerLearningCourseListResponse;
import com.zhibo.live.viewer.dto.ViewerLearningLessonContentResponse;
import com.zhibo.live.viewer.dto.ViewerLearningSubCourseItem;
import com.zhibo.live.watch.dto.WatchActivityItem;
import com.zhibo.live.watch.dto.WatchActivityListResponse;
import com.zhibo.material.MaterialArticleEntity;
import com.zhibo.material.MaterialArticleRepository;
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialRepository;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 小程序「我的学习」：有观看权益的直播 / 课程。 */
@Service
@RequiredArgsConstructor
public class ViewerLearningService {

    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ActivityRepository activityRepository;
    private final CourseRepository courseRepository;
    private final CourseUserRepository courseUserRepository;
    private final CourseCatalogNodeRepository catalogNodeRepository;
    private final CourseLessonProgressRepository progressRepository;
    private final MaterialRepository materialRepository;
    private final MaterialArticleRepository materialArticleRepository;

    @Transactional(readOnly = true)
    public WatchActivityListResponse listEntitledLives(long viewerId, int page, int pageSize, Integer state) {
        assertPage(page, pageSize);
        boolean statesEmpty = state == null || state == 0;
        List<Integer> statesParam = statesEmpty ? List.of(0) : List.of(state);
        PageRequest pageable = PageRequest.of(
                page - 1,
                pageSize,
                Sort.by(Sort.Order.desc("startTime"), Sort.Order.desc("createdTime")));
        Page<ActivityEntity> result =
                activityRepository.searchEntitledForViewer(viewerId, statesEmpty, statesParam, pageable);

        WatchActivityListResponse response = new WatchActivityListResponse();
        response.setTotal(result.getTotalElements());
        response.setList(result.getContent().stream().map(this::toLiveItem).toList());
        return response;
    }

    @Transactional(readOnly = true)
    public ViewerLearningCourseListResponse listEntitledCourses(long viewerId, int page, int pageSize) {
        assertPage(page, pageSize);
        LocalDateTime now = LocalDateTime.now();
        PageRequest pageable = PageRequest.of(page - 1, pageSize);
        Page<CourseEntity> result = courseRepository.searchEntitledForViewer(viewerId, now, pageable);

        ViewerLearningCourseListResponse response = new ViewerLearningCourseListResponse();
        response.setTotal(result.getTotalElements());
        response.setList(
                result.getContent().stream().map(course -> toCourseItem(course, viewerId)).toList());
        return response;
    }

    @Transactional(readOnly = true)
    public ViewerLearningCourseDetailResponse getCourseDetail(long viewerId, String courseIdRaw) {
        CourseEntity course = requireEntitledCourse(viewerId, courseIdRaw);
        String courseId = course.getId();

        List<CourseCatalogNodeEntity> ordered =
                CatalogSiblingOrder.orderFlat(catalogNodeRepository.findActiveByCourseId(courseId));
        Map<Long, Long> parentOf = new HashMap<>();
        Map<Long, CourseCatalogNodeEntity> byId = new HashMap<>();
        for (CourseCatalogNodeEntity n : ordered) {
            parentOf.put(n.getId(), n.getParentId());
            byId.put(n.getId(), n);
        }

        Map<Long, Integer> lessonCountByChapter = new HashMap<>();
        Map<Long, Integer> lessonCountBySub = new HashMap<>();
        for (CourseCatalogNodeEntity n : ordered) {
            if (n.getNodeType() != CourseCatalogNodeType.LESSON) {
                continue;
            }
            if (n.getParentId() > 0) {
                CourseCatalogNodeEntity parent = byId.get(n.getParentId());
                if (parent != null && parent.getNodeType() == CourseCatalogNodeType.CHAPTER) {
                    lessonCountByChapter.merge(parent.getId(), 1, Integer::sum);
                }
            }
            Long subId = resolveSubcourseAncestor(n.getParentId(), parentOf, byId);
            if (subId != null) {
                lessonCountBySub.merge(subId, 1, Integer::sum);
            }
        }

        Set<String> liveIds = new HashSet<>();
        for (CourseCatalogNodeEntity n : ordered) {
            if (n.getNodeType() == CourseCatalogNodeType.LESSON
                    && n.getContentKind() != null
                    && n.getContentKind() == CourseCatalogContentKind.LIVE
                    && n.getContentId() != null
                    && !n.getContentId().isBlank()) {
                liveIds.add(n.getContentId());
            }
        }
        Map<String, ActivityEntity> lives = new HashMap<>();
        if (!liveIds.isEmpty()) {
            for (ActivityEntity a : activityRepository.findAllById(liveIds)) {
                if (a.getDeletedTime() == null) {
                    lives.put(a.getId(), a);
                }
            }
        }

        Map<Long, CourseLessonProgressEntity> progressByLesson = new HashMap<>();
        for (CourseLessonProgressEntity p : progressRepository.findByCourseIdAndViewerId(courseId, viewerId)) {
            progressByLesson.put(p.getLessonNodeId(), p);
        }

        List<ViewerLearningSubCourseItem> subCourses = new ArrayList<>();
        List<ViewerLearningCatalogNode> nodes = new ArrayList<>();
        for (CourseCatalogNodeEntity n : ordered) {
            if (n.getNodeType() == CourseCatalogNodeType.SUBCOURSE && n.getParentId() == 0L) {
                ViewerLearningSubCourseItem sub = new ViewerLearningSubCourseItem();
                sub.setId(n.getId());
                sub.setTitle(n.getTitle());
                sub.setCoverUrl(n.getCoverUrl());
                sub.setLessonCount(lessonCountBySub.getOrDefault(n.getId(), 0));
                subCourses.add(sub);
            }
            nodes.add(
                    toCatalogNode(
                            n, lessonCountByChapter, lessonCountBySub, lives, progressByLesson));
        }

        ViewerLearningCourseDetailResponse response = new ViewerLearningCourseDetailResponse();
        response.setId(course.getId());
        response.setTitle(course.getTitle());
        response.setCoverUrl(course.getCoverUrl());
        response.setIntroduction(course.getIntroduction());
        response.setDetailHtml(course.getDetailHtml());
        response.setValidityLabel(course.getValidityLabel());
        response.setSubCourses(subCourses);
        response.setNodes(nodes);
        return response;
    }

    @Transactional(readOnly = true)
    public ViewerLearningLessonContentResponse getLessonContent(
            long viewerId, String courseIdRaw, long lessonNodeId) {
        if (lessonNodeId <= 0) {
            throw new IllegalArgumentException("小节 id 无效");
        }
        CourseEntity course = requireEntitledCourse(viewerId, courseIdRaw);
        String courseId = course.getId();
        CourseCatalogNodeEntity lesson =
                catalogNodeRepository
                        .findByIdAndCourseIdAndDeletedTimeIsNull(lessonNodeId, courseId)
                        .orElseThrow(() -> new IllegalArgumentException("小节不存在"));
        if (lesson.getNodeType() != CourseCatalogNodeType.LESSON) {
            throw new IllegalArgumentException("节点不是小节");
        }
        Integer kindObj = lesson.getContentKind();
        if (kindObj == null || !CourseCatalogContentKind.isKnown(kindObj)) {
            throw new IllegalArgumentException("小节内容类型无效");
        }
        int kind = kindObj;
        String contentId = lesson.getContentId();
        if (contentId == null || contentId.isBlank()) {
            throw new IllegalArgumentException("小节未绑定内容");
        }

        ViewerLearningLessonContentResponse resp = new ViewerLearningLessonContentResponse();
        resp.setLessonNodeId(lesson.getId());
        resp.setTitle(lesson.getTitle());
        resp.setContentKind(kind);
        resp.setContentId(contentId);

        progressRepository
                .findByCourseIdAndLessonNodeIdAndViewerId(courseId, lesson.getId(), viewerId)
                .ifPresentOrElse(
                        p -> {
                            resp.setParticipateStatus(p.getParticipateStatus());
                            resp.setProgressPercent(p.getProgressPercent());
                            resp.setProgressBarSec(p.getProgressBarSec());
                        },
                        () -> {
                            resp.setParticipateStatus(CourseLessonParticipateStatus.NOT_STARTED);
                            resp.setProgressPercent(0);
                        });

        if (kind == CourseCatalogContentKind.LIVE) {
            resp.setActivityId(contentId);
            return resp;
        }

        MaterialEntity material =
                materialRepository
                        .findById(contentId)
                        .filter(m -> m.getDeletedTime() == null)
                        .orElseThrow(() -> new IllegalArgumentException("素材不存在"));
        resp.setMaterialUrl(material.getUrl());
        resp.setMimeType(material.getMimeType());
        resp.setMaterialName(material.getName());
        if (kind == CourseCatalogContentKind.ARTICLE) {
            MaterialArticleEntity article =
                    materialArticleRepository
                            .findById(contentId)
                            .orElseThrow(() -> new IllegalArgumentException("文章正文不存在"));
            resp.setArticleHtml(article.getContent());
            resp.setAuthor(article.getAuthor());
        }
        return resp;
    }

    private CourseEntity requireEntitledCourse(long viewerId, String courseIdRaw) {
        String courseId = UuidIds.require(courseIdRaw, "课程 id 不能为空", "课程 id 无效");
        CourseEntity course = courseRepository
                .findById(courseId)
                .filter(c -> c.getDeletedTime() == null)
                .orElseThrow(() -> new IllegalArgumentException("课程不存在"));
        CourseUserEntity row = courseUserRepository
                .findByCourseIdAndViewerIdAndEntitlementStatus(courseId, viewerId, 1)
                .orElseThrow(() -> new IllegalArgumentException("无观看权限"));
        LocalDateTime now = LocalDateTime.now();
        String state = CourseEntitlementSupport.resolveState(
                row.getEntitlementStatus(),
                course.getAccessValidityType(),
                row.getEntitlementExpireTime(),
                now);
        if (!"valid".equals(state)) {
            throw new IllegalArgumentException("无观看权限");
        }
        return course;
    }

    private ViewerLearningCatalogNode toCatalogNode(
            CourseCatalogNodeEntity n,
            Map<Long, Integer> lessonCountByChapter,
            Map<Long, Integer> lessonCountBySub,
            Map<String, ActivityEntity> lives,
            Map<Long, CourseLessonProgressEntity> progressByLesson) {
        ViewerLearningCatalogNode view = new ViewerLearningCatalogNode();
        view.setId(n.getId());
        view.setParentId(n.getParentId());
        view.setNodeType(n.getNodeType());
        view.setTitle(n.getTitle());
        view.setCoverUrl(n.getCoverUrl());
        if (n.getNodeType() == CourseCatalogNodeType.CHAPTER) {
            view.setLessonCount(lessonCountByChapter.getOrDefault(n.getId(), 0));
        } else if (n.getNodeType() == CourseCatalogNodeType.SUBCOURSE) {
            view.setLessonCount(lessonCountBySub.getOrDefault(n.getId(), 0));
        } else if (n.getNodeType() == CourseCatalogNodeType.LESSON) {
            view.setContentKind(n.getContentKind());
            view.setContentId(n.getContentId());
            view.setContentKindLabel(contentKindLabel(n.getContentKind()));
            if (n.getContentKind() != null
                    && n.getContentKind() == CourseCatalogContentKind.LIVE
                    && n.getContentId() != null) {
                ActivityEntity live = lives.get(n.getContentId());
                if (live != null && Boolean.TRUE.equals(live.getPlaybackEnabled())) {
                    view.setMetaLabel("支持回放");
                }
            }
            CourseLessonProgressEntity progress = progressByLesson.get(n.getId());
            if (progress != null) {
                view.setParticipateStatus(progress.getParticipateStatus());
                view.setProgressPercent(progress.getProgressPercent());
            } else {
                view.setParticipateStatus(CourseLessonParticipateStatus.NOT_STARTED);
                view.setProgressPercent(0);
            }
        }
        return view;
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

    private static Long resolveSubcourseAncestor(
            long parentId,
            Map<Long, Long> parentOf,
            Map<Long, CourseCatalogNodeEntity> byId) {
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

    private ViewerLearningCourseItem toCourseItem(CourseEntity course, long viewerId) {
        ViewerLearningCourseItem item = new ViewerLearningCourseItem();
        item.setId(course.getId());
        item.setTitle(course.getTitle());
        item.setCoverUrl(course.getCoverUrl());
        item.setLessonCount(course.getContentCount());
        item.setSubCourseCount(
                (int)
                        catalogNodeRepository.countActiveByCourseIdAndNodeType(
                                course.getId(), CourseCatalogNodeType.SUBCOURSE));
        courseUserRepository
                .findByCourseIdAndViewerIdAndEntitlementStatus(course.getId(), viewerId, 1)
                .map(CourseUserEntity::getCreatedTime)
                .ifPresent(t -> item.setAcquiredTime(t.format(TIME_FMT)));
        return item;
    }

    private WatchActivityItem toLiveItem(ActivityEntity entity) {
        WatchActivityItem item = new WatchActivityItem();
        item.setId(entity.getId());
        item.setTitle(entity.getTitle());
        item.setState(entity.getState());
        item.setStartTime(entity.getStartTime());
        item.setCoverUrl(entity.getCoverUrl());
        return item;
    }

    private static void assertPage(int page, int pageSize) {
        if (page < 1) {
            throw new IllegalArgumentException("page 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new IllegalArgumentException("pageSize 需在 1~100");
        }
    }
}
