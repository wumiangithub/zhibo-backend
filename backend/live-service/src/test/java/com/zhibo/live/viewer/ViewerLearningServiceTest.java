package com.zhibo.live.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.course.CourseCatalogContentKind;
import com.zhibo.course.CourseCatalogNodeEntity;
import com.zhibo.course.CourseCatalogNodeRepository;
import com.zhibo.course.CourseCatalogNodeType;
import com.zhibo.course.CourseEntity;
import com.zhibo.course.CourseLessonParticipateStatus;
import com.zhibo.course.CourseLessonProgressEntity;
import com.zhibo.course.CourseLessonProgressRepository;
import com.zhibo.course.CourseRepository;
import com.zhibo.course.CourseUserEntity;
import com.zhibo.course.CourseUserRepository;
import com.zhibo.material.MaterialArticleEntity;
import com.zhibo.material.MaterialArticleRepository;
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ViewerLearningServiceTest {

    private static final String ID = "a1b2c3d4e5f67890abcdef1234567890";
    private static final String COURSE_ID = "b1b2c3d4e5f67890abcdef1234567890";
    private static final String LIVE_ID = "c1b2c3d4e5f67890abcdef1234567890";
    private static final String MATERIAL_ID = "d1b2c3d4e5f67890abcdef1234567890";

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseUserRepository courseUserRepository;

    @Mock
    private CourseCatalogNodeRepository catalogNodeRepository;

    @Mock
    private CourseLessonProgressRepository progressRepository;

    @Mock
    private MaterialRepository materialRepository;

    @Mock
    private MaterialArticleRepository materialArticleRepository;

    @InjectMocks
    private ViewerLearningService learningService;

    @Test
    void listEntitledLives_rejectsPageBelowOne() {
        assertThrows(
                IllegalArgumentException.class, () -> learningService.listEntitledLives(9L, 0, 20, null));
    }

    @Test
    void listEntitledLives_mapsRows() {
        ActivityEntity row = new ActivityEntity();
        row.setId(ID);
        row.setTitle("权益课");
        row.setState(1);
        row.setStartTime("2026-10-28 20:00:00");
        row.setCoverUrl("https://cover");

        when(activityRepository.searchEntitledForViewer(eq(9L), anyBoolean(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(row)));

        var resp = learningService.listEntitledLives(9L, 1, 20, null);
        assertEquals(1, resp.getTotal());
        assertEquals(ID, resp.getList().get(0).getId());
        assertEquals("权益课", resp.getList().get(0).getTitle());

        ArgumentCaptor<Pageable> pageableCap = ArgumentCaptor.forClass(Pageable.class);
        verify(activityRepository)
                .searchEntitledForViewer(eq(9L), eq(true), eq(List.of(0)), pageableCap.capture());
        assertEquals(0, pageableCap.getValue().getPageNumber());
        assertEquals(20, pageableCap.getValue().getPageSize());
    }

    @Test
    void listEntitledCourses_mapsRows() {
        CourseEntity course = new CourseEntity();
        course.setId(COURSE_ID);
        course.setTitle("拓维班");
        course.setCoverUrl("https://course-cover");
        course.setContentCount(315);

        CourseUserEntity cu = new CourseUserEntity();
        cu.setCreatedTime(LocalDateTime.of(2026, 9, 17, 19, 29, 0));

        when(courseRepository.searchEntitledForViewer(eq(9L), any(), any()))
                .thenReturn(new PageImpl<>(List.of(course)));
        when(catalogNodeRepository.countActiveByCourseIdAndNodeType(
                        COURSE_ID, CourseCatalogNodeType.SUBCOURSE))
                .thenReturn(5L);
        when(courseUserRepository.findByCourseIdAndViewerIdAndEntitlementStatus(COURSE_ID, 9L, 1))
                .thenReturn(Optional.of(cu));

        var resp = learningService.listEntitledCourses(9L, 1, 20);
        assertEquals(1, resp.getTotal());
        assertEquals(COURSE_ID, resp.getList().get(0).getId());
        assertEquals("拓维班", resp.getList().get(0).getTitle());
        assertEquals(5, resp.getList().get(0).getSubCourseCount());
        assertEquals(315, resp.getList().get(0).getLessonCount());
        assertEquals("2026-09-17 19:29:00", resp.getList().get(0).getAcquiredTime());
    }

    @Test
    void getCourseDetail_rejectsWithoutEntitlement() {
        CourseEntity course = new CourseEntity();
        course.setId(COURSE_ID);
        course.setAccessValidityType("long");
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(courseUserRepository.findByCourseIdAndViewerIdAndEntitlementStatus(COURSE_ID, 9L, 1))
                .thenReturn(Optional.empty());

        assertThrows(
                IllegalArgumentException.class, () -> learningService.getCourseDetail(9L, COURSE_ID));
    }

    @Test
    void getCourseDetail_mapsCatalogAndProgress() {
        CourseEntity course = new CourseEntity();
        course.setId(COURSE_ID);
        course.setTitle("拓维班");
        course.setCoverUrl("https://cover");
        course.setIntroduction("简介");
        course.setDetailHtml("<p>详情</p>");
        course.setValidityLabel("360天有效");
        course.setAccessValidityType("long");

        CourseUserEntity cu = new CourseUserEntity();
        cu.setEntitlementStatus(1);

        CourseCatalogNodeEntity sub = node(10L, 0L, CourseCatalogNodeType.SUBCOURSE, "子课A", null, null);
        sub.setCoverUrl("https://sub");
        CourseCatalogNodeEntity chapter =
                node(11L, 10L, CourseCatalogNodeType.CHAPTER, "章节1", null, null);
        CourseCatalogNodeEntity lesson =
                node(12L, 11L, CourseCatalogNodeType.LESSON, "第一节", CourseCatalogContentKind.LIVE, LIVE_ID);

        ActivityEntity live = new ActivityEntity();
        live.setId(LIVE_ID);
        live.setPlaybackEnabled(true);

        CourseLessonProgressEntity progress = new CourseLessonProgressEntity();
        progress.setLessonNodeId(12L);
        progress.setParticipateStatus(CourseLessonParticipateStatus.COMPLETED);
        progress.setProgressPercent(100);

        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(courseUserRepository.findByCourseIdAndViewerIdAndEntitlementStatus(COURSE_ID, 9L, 1))
                .thenReturn(Optional.of(cu));
        when(catalogNodeRepository.findActiveByCourseId(COURSE_ID))
                .thenReturn(List.of(sub, chapter, lesson));
        when(activityRepository.findAllById(any())).thenReturn(List.of(live));
        when(progressRepository.findByCourseIdAndViewerId(COURSE_ID, 9L))
                .thenReturn(List.of(progress));

        var resp = learningService.getCourseDetail(9L, COURSE_ID);
        assertEquals(COURSE_ID, resp.getId());
        assertEquals("360天有效", resp.getValidityLabel());
        assertEquals(1, resp.getSubCourses().size());
        assertEquals(10L, resp.getSubCourses().get(0).getId());
        assertEquals(1, resp.getSubCourses().get(0).getLessonCount());
        assertEquals(3, resp.getNodes().size());
        var chapterNode = resp.getNodes().stream().filter(n -> n.getId() == 11L).findFirst().orElseThrow();
        assertEquals(1, chapterNode.getLessonCount());
        var lessonNode = resp.getNodes().stream().filter(n -> n.getId() == 12L).findFirst().orElseThrow();
        assertEquals("直播", lessonNode.getContentKindLabel());
        assertEquals("支持回放", lessonNode.getMetaLabel());
        assertEquals(LIVE_ID, lessonNode.getContentId());
        assertEquals(CourseLessonParticipateStatus.COMPLETED, lessonNode.getParticipateStatus());
        assertEquals(100, lessonNode.getProgressPercent());
    }

    @Test
    void getLessonContent_mapsImageMaterial() {
        stubEntitledCourse();
        CourseCatalogNodeEntity lesson =
                node(
                        12L,
                        0L,
                        CourseCatalogNodeType.LESSON,
                        "看图",
                        CourseCatalogContentKind.IMAGE,
                        MATERIAL_ID);
        MaterialEntity material = new MaterialEntity();
        material.setId(MATERIAL_ID);
        material.setName("封面图");
        material.setUrl("https://img");
        material.setMimeType("image/jpeg");

        when(catalogNodeRepository.findByIdAndCourseIdAndDeletedTimeIsNull(12L, COURSE_ID))
                .thenReturn(Optional.of(lesson));
        when(progressRepository.findByCourseIdAndLessonNodeIdAndViewerId(COURSE_ID, 12L, 9L))
                .thenReturn(Optional.empty());
        when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.of(material));

        var resp = learningService.getLessonContent(9L, COURSE_ID, 12L);
        assertEquals(12L, resp.getLessonNodeId());
        assertEquals(CourseCatalogContentKind.IMAGE, resp.getContentKind());
        assertEquals("https://img", resp.getMaterialUrl());
        assertEquals("封面图", resp.getMaterialName());
        assertEquals(CourseLessonParticipateStatus.NOT_STARTED, resp.getParticipateStatus());
    }

    @Test
    void getLessonContent_mapsArticle() {
        stubEntitledCourse();
        CourseCatalogNodeEntity lesson =
                node(
                        15L,
                        0L,
                        CourseCatalogNodeType.LESSON,
                        "读文",
                        CourseCatalogContentKind.ARTICLE,
                        MATERIAL_ID);
        MaterialEntity material = new MaterialEntity();
        material.setId(MATERIAL_ID);
        material.setName("文章A");
        material.setType("article");

        MaterialArticleEntity article = new MaterialArticleEntity();
        article.setId(MATERIAL_ID);
        article.setAuthor("张三");
        article.setContent("<p>hello</p>");

        when(catalogNodeRepository.findByIdAndCourseIdAndDeletedTimeIsNull(15L, COURSE_ID))
                .thenReturn(Optional.of(lesson));
        when(progressRepository.findByCourseIdAndLessonNodeIdAndViewerId(COURSE_ID, 15L, 9L))
                .thenReturn(Optional.empty());
        when(materialRepository.findById(MATERIAL_ID)).thenReturn(Optional.of(material));
        when(materialArticleRepository.findById(MATERIAL_ID)).thenReturn(Optional.of(article));

        var resp = learningService.getLessonContent(9L, COURSE_ID, 15L);
        assertEquals("<p>hello</p>", resp.getArticleHtml());
        assertEquals("张三", resp.getAuthor());
        assertEquals("文章A", resp.getMaterialName());
    }

    @Test
    void getLessonContent_liveReturnsActivityId() {
        stubEntitledCourse();
        CourseCatalogNodeEntity lesson =
                node(
                        12L,
                        0L,
                        CourseCatalogNodeType.LESSON,
                        "直播课",
                        CourseCatalogContentKind.LIVE,
                        LIVE_ID);
        when(catalogNodeRepository.findByIdAndCourseIdAndDeletedTimeIsNull(12L, COURSE_ID))
                .thenReturn(Optional.of(lesson));
        when(progressRepository.findByCourseIdAndLessonNodeIdAndViewerId(COURSE_ID, 12L, 9L))
                .thenReturn(Optional.empty());

        var resp = learningService.getLessonContent(9L, COURSE_ID, 12L);
        assertEquals(LIVE_ID, resp.getActivityId());
        assertEquals(LIVE_ID, resp.getContentId());
    }

    private void stubEntitledCourse() {
        CourseEntity course = new CourseEntity();
        course.setId(COURSE_ID);
        course.setAccessValidityType("long");
        CourseUserEntity cu = new CourseUserEntity();
        cu.setEntitlementStatus(1);
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
        when(courseUserRepository.findByCourseIdAndViewerIdAndEntitlementStatus(COURSE_ID, 9L, 1))
                .thenReturn(Optional.of(cu));
    }

    private static CourseCatalogNodeEntity node(
            long id, long parentId, int type, String title, Integer kind, String contentId) {
        CourseCatalogNodeEntity n = new CourseCatalogNodeEntity();
        n.setId(id);
        n.setParentId(parentId);
        n.setNodeType(type);
        n.setTitle(title);
        n.setContentKind(kind);
        n.setContentId(contentId);
        n.setSortOrder(0);
        n.setCreatedTime(LocalDateTime.of(2026, 1, 1, 0, 0));
        return n;
    }
}
