package com.zhibo.live.watch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.course.CourseCatalogContentKind;
import com.zhibo.course.CourseCatalogNodeEntity;
import com.zhibo.course.CourseCatalogNodeRepository;
import com.zhibo.course.CourseCatalogNodeType;
import com.zhibo.course.CourseEntity;
import com.zhibo.course.CourseLessonParticipateStatus;
import com.zhibo.course.CourseLessonProgressEntity;
import com.zhibo.course.CourseLessonProgressRepository;
import com.zhibo.course.CourseRepository;
import com.zhibo.live.watch.dto.CourseLessonProgressRequest;
import com.zhibo.live.watch.dto.CourseLessonProgressResponse;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseLessonProgressServiceTest {

    private static final String COURSE_ID = "a".repeat(32);

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseCatalogNodeRepository catalogNodeRepository;
    @Mock
    private CourseLessonProgressRepository progressRepository;

    private CourseLessonProgressService service;

    @BeforeEach
    void setUp() {
        service = new CourseLessonProgressService(
                courseRepository, catalogNodeRepository, progressRepository);
    }

    @Test
    void openImage_completes() {
        stubCourseAndLesson(12L, CourseCatalogContentKind.IMAGE);
        when(progressRepository.findByCourseIdAndLessonNodeIdAndViewerId(COURSE_ID, 12L, 7L))
                .thenReturn(Optional.empty());
        when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CourseLessonProgressRequest req = new CourseLessonProgressRequest();
        req.setLessonNodeId(12L);
        req.setEvent("open");

        CourseLessonProgressResponse resp = service.report(COURSE_ID, 7L, req);
        assertTrue(resp.isCompleted());
        assertEquals(CourseLessonParticipateStatus.COMPLETED, resp.getParticipateStatus());
        assertEquals(100, resp.getProgressPercent());
    }

    @Test
    void video_reaches100_completes() {
        stubCourseAndLesson(3L, CourseCatalogContentKind.VIDEO);
        CourseLessonProgressEntity existing = new CourseLessonProgressEntity();
        existing.setCourseId(COURSE_ID);
        existing.setLessonNodeId(3L);
        existing.setViewerId(7L);
        existing.setParticipateStatus(CourseLessonParticipateStatus.IN_PROGRESS);
        existing.setProgressPercent(80);
        when(progressRepository.findByCourseIdAndLessonNodeIdAndViewerId(COURSE_ID, 3L, 7L))
                .thenReturn(Optional.of(existing));
        when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CourseLessonProgressRequest req = new CourseLessonProgressRequest();
        req.setLessonNodeId(3L);
        req.setEvent("heartbeat");
        req.setDeltaLearnDurationSec(30L);
        req.setProgressPercent(100);
        req.setProgressBarSec(600);

        CourseLessonProgressResponse resp = service.report(COURSE_ID, 7L, req);
        assertTrue(resp.isCompleted());
        assertEquals(30L, resp.getLearnDurationSec());
        assertEquals(600, resp.getProgressBarSec());

        ArgumentCaptor<CourseLessonProgressEntity> cap =
                ArgumentCaptor.forClass(CourseLessonProgressEntity.class);
        verify(progressRepository).save(cap.capture());
        assertEquals(CourseLessonParticipateStatus.COMPLETED, cap.getValue().getParticipateStatus());
    }

    @Test
    void liveEnded_completesLive() {
        stubCourseAndLesson(9L, CourseCatalogContentKind.LIVE);
        when(progressRepository.findByCourseIdAndLessonNodeIdAndViewerId(COURSE_ID, 9L, 7L))
                .thenReturn(Optional.empty());
        when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CourseLessonProgressRequest req = new CourseLessonProgressRequest();
        req.setLessonNodeId(9L);
        req.setEvent("live_ended");

        assertTrue(service.report(COURSE_ID, 7L, req).isCompleted());
    }

    @Test
    void scrollBottom_completesArticle() {
        stubCourseAndLesson(5L, CourseCatalogContentKind.ARTICLE);
        when(progressRepository.findByCourseIdAndLessonNodeIdAndViewerId(COURSE_ID, 5L, 7L))
                .thenReturn(Optional.empty());
        when(progressRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CourseLessonProgressRequest req = new CourseLessonProgressRequest();
        req.setLessonNodeId(5L);
        req.setEvent("scroll_bottom");

        assertTrue(service.report(COURSE_ID, 7L, req).isCompleted());
    }

    private void stubCourseAndLesson(long lessonId, int contentKind) {
        CourseEntity course = new CourseEntity();
        course.setId(COURSE_ID);
        when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

        CourseCatalogNodeEntity lesson = new CourseCatalogNodeEntity();
        lesson.setId(lessonId);
        lesson.setCourseId(COURSE_ID);
        lesson.setNodeType(CourseCatalogNodeType.LESSON);
        lesson.setContentKind(contentKind);
        lesson.setTitle("t");
        when(catalogNodeRepository.findByIdAndCourseIdAndDeletedTimeIsNull(eq(lessonId), eq(COURSE_ID)))
                .thenReturn(Optional.of(lesson));
    }
}
