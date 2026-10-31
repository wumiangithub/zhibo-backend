package com.zhibo.admin.course;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.course.dto.BindLivesRequest;
import com.zhibo.admin.course.dto.BindLivesResponse;
import com.zhibo.admin.course.dto.CatalogNodeView;
import com.zhibo.admin.course.dto.CreateChapterRequest;
import com.zhibo.admin.course.dto.CatalogNodeIdResponse;
import com.zhibo.admin.course.dto.CreateSubcourseRequest;
import com.zhibo.admin.course.dto.DeleteChapterRequest;
import com.zhibo.admin.course.dto.DeleteSubcourseRequest;
import com.zhibo.admin.course.dto.ReorderCatalogItem;
import com.zhibo.admin.course.dto.ReorderCatalogRequest;
import com.zhibo.admin.course.dto.UpdateChapterRequest;
import com.zhibo.admin.course.dto.UpdateSubcourseRequest;
import com.zhibo.common.UuidIds;
import com.zhibo.course.CourseCatalogContentKind;
import com.zhibo.course.CourseCatalogNodeEntity;
import com.zhibo.course.CourseCatalogNodeRepository;
import com.zhibo.course.CourseCatalogNodeType;
import com.zhibo.course.CourseEntity;
import com.zhibo.course.CourseRepository;
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseCatalogServiceTest {

    @Mock
    private CourseRepository courseRepository;
    @Mock
    private CourseCatalogNodeRepository catalogRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private MaterialRepository materialRepository;

    @InjectMocks
    private CourseCatalogService catalogService;

    @Test
    void bindLives_createsLessonsAndSyncsCount() {
        CourseEntity course = course();
        String actId = UuidIds.newId();
        when(catalogRepository.countActiveByCourseIdAndNodeType(course.getId(), CourseCatalogNodeType.SUBCOURSE))
                .thenReturn(0L);
        when(catalogRepository.findActiveByCourseIdAndParentId(course.getId(), 0L)).thenReturn(List.of());
        when(catalogRepository.existsActiveContent(course.getId(), CourseCatalogContentKind.LIVE, actId))
                .thenReturn(false);
        ActivityEntity activity = new ActivityEntity();
        activity.setId(actId);
        activity.setTitle("一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十超");
        when(activityRepository.findById(actId)).thenReturn(Optional.of(activity));
        when(catalogRepository.save(any())).thenAnswer(inv -> {
            CourseCatalogNodeEntity n = inv.getArgument(0);
            n.setId(11L);
            return n;
        });
        when(catalogRepository.countActiveByCourseIdAndNodeType(course.getId(), CourseCatalogNodeType.LESSON))
                .thenReturn(1L);

        BindLivesRequest body = new BindLivesRequest();
        body.setParentId(0L);
        body.setActivityIds(List.of(actId));

        BindLivesResponse response = catalogService.bindLives(course.getId(), body);

        assertEquals(1, response.getList().size());
        assertEquals(1, response.getContentCount());
        assertEquals(30, response.getList().get(0).getTitle().length());
        assertEquals(CourseCatalogContentKind.LIVE, response.getList().get(0).getContentKind());
        assertEquals(0, response.getList().get(0).getSortOrder());
        verify(courseRepository).save(course);
        assertEquals(1, course.getContentCount());
    }

    @Test
    void bindLives_rejectsDuplicate() {
        CourseEntity course = course();
        String actId = UuidIds.newId();
        when(catalogRepository.countActiveByCourseIdAndNodeType(course.getId(), CourseCatalogNodeType.SUBCOURSE))
                .thenReturn(0L);
        when(catalogRepository.findActiveByCourseIdAndParentId(course.getId(), 0L)).thenReturn(List.of());
        when(catalogRepository.existsActiveContent(course.getId(), CourseCatalogContentKind.LIVE, actId))
                .thenReturn(true);

        BindLivesRequest body = new BindLivesRequest();
        body.setParentId(0L);
        body.setActivityIds(List.of(actId));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> catalogService.bindLives(course.getId(), body));
        assertTrue(ex.getMessage().contains("不可重复"));
    }

    @Test
    void bindLives_rejectsRootWhenSubcourseExists() {
        CourseEntity course = course();
        when(catalogRepository.countActiveByCourseIdAndNodeType(course.getId(), CourseCatalogNodeType.SUBCOURSE))
                .thenReturn(1L);

        BindLivesRequest body = new BindLivesRequest();
        body.setParentId(0L);
        body.setActivityIds(List.of(UuidIds.newId()));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> catalogService.bindLives(course.getId(), body));
        assertTrue(ex.getMessage().contains("根目录"));
    }

    @Test
    void createSubcourse_requiresMoveConfirmWhenRootHasContent() {
        CourseEntity course = course();
        when(materialRepository.findById("cover-1")).thenReturn(Optional.of(cover("cover-1")));
        when(catalogRepository.countActiveByCourseIdAndNodeType(course.getId(), CourseCatalogNodeType.SUBCOURSE))
                .thenReturn(0L);
        when(catalogRepository.countActiveRootByCourseIdAndNodeTypes(eq(course.getId()), any()))
                .thenReturn(2L);

        CreateSubcourseRequest body = new CreateSubcourseRequest();
        body.setTitle("子课程1");
        body.setCoverMaterialId("cover-1");
        body.setMoveRootContents(false);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> catalogService.createSubcourse(course.getId(), body));
        assertTrue(ex.getMessage().contains("迁入"));
        verify(catalogRepository, never()).save(any());
    }

    @Test
    void createSubcourse_movesRootContentsOnFirst() {
        CourseEntity course = course();
        when(materialRepository.findById("cover-1")).thenReturn(Optional.of(cover("cover-1")));
        when(catalogRepository.countActiveByCourseIdAndNodeType(course.getId(), CourseCatalogNodeType.SUBCOURSE))
                .thenReturn(0L);
        when(catalogRepository.countActiveRootByCourseIdAndNodeTypes(eq(course.getId()), any()))
                .thenReturn(2L);
        when(catalogRepository.findActiveByCourseIdAndParentId(course.getId(), 0L)).thenReturn(List.of());
        when(catalogRepository.save(any())).thenAnswer(inv -> {
            CourseCatalogNodeEntity n = inv.getArgument(0);
            n.setId(99L);
            return n;
        });
        when(catalogRepository.findActiveByCourseId(course.getId())).thenReturn(List.of());

        CreateSubcourseRequest body = new CreateSubcourseRequest();
        body.setTitle("子课程1");
        body.setCoverMaterialId("cover-1");
        body.setMoveRootContents(true);

        CatalogNodeView view = catalogService.createSubcourse(course.getId(), body);

        assertEquals(99L, view.getId());
        verify(catalogRepository).reparentRootNodes(
                eq(course.getId()),
                eq(99L),
                eq(List.of(CourseCatalogNodeType.CHAPTER, CourseCatalogNodeType.LESSON)));
    }

    @Test
    void deleteSubcourse_keepContents_onlySoftDeletesSub() {
        CourseEntity course = course();
        CourseCatalogNodeEntity sub = new CourseCatalogNodeEntity();
        sub.setId(5L);
        sub.setCourseId(course.getId());
        sub.setNodeType(CourseCatalogNodeType.SUBCOURSE);
        sub.setParentId(0L);
        when(catalogRepository.findByIdAndCourseIdAndDeletedTimeIsNull(5L, course.getId()))
                .thenReturn(Optional.of(sub));
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeleteSubcourseRequest body = new DeleteSubcourseRequest();
        body.setId(5L);
        body.setKeepContents(true);

        CatalogNodeIdResponse response = catalogService.deleteSubcourse(course.getId(), body);

        assertEquals(5L, response.getId());
        assertTrue(sub.getDeletedTime() != null);
        verify(catalogRepository, never()).findActiveByCourseIdAndParentId(any(), any(Long.class));
    }

    @Test
    void deleteSubcourse_keepContents_withTarget_reparentsDirectChildren() {
        CourseEntity course = course();
        CourseCatalogNodeEntity sub = new CourseCatalogNodeEntity();
        sub.setId(5L);
        sub.setCourseId(course.getId());
        sub.setNodeType(CourseCatalogNodeType.SUBCOURSE);
        sub.setParentId(0L);
        CourseCatalogNodeEntity target = new CourseCatalogNodeEntity();
        target.setId(8L);
        target.setCourseId(course.getId());
        target.setNodeType(CourseCatalogNodeType.SUBCOURSE);
        target.setParentId(0L);
        CourseCatalogNodeEntity chapter = new CourseCatalogNodeEntity();
        chapter.setId(51L);
        chapter.setCourseId(course.getId());
        chapter.setNodeType(CourseCatalogNodeType.CHAPTER);
        chapter.setParentId(5L);
        CourseCatalogNodeEntity rootLesson = new CourseCatalogNodeEntity();
        rootLesson.setId(52L);
        rootLesson.setCourseId(course.getId());
        rootLesson.setNodeType(CourseCatalogNodeType.LESSON);
        rootLesson.setParentId(5L);
        when(catalogRepository.findByIdAndCourseIdAndDeletedTimeIsNull(5L, course.getId()))
                .thenReturn(Optional.of(sub));
        when(catalogRepository.findByIdAndCourseIdAndDeletedTimeIsNull(8L, course.getId()))
                .thenReturn(Optional.of(target));
        when(catalogRepository.findActiveByCourseIdAndParentId(course.getId(), 5L))
                .thenReturn(List.of(chapter, rootLesson));
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeleteSubcourseRequest body = new DeleteSubcourseRequest();
        body.setId(5L);
        body.setKeepContents(true);
        body.setTargetSubcourseId(8L);

        CatalogNodeIdResponse response = catalogService.deleteSubcourse(course.getId(), body);

        assertEquals(5L, response.getId());
        assertEquals(8L, chapter.getParentId());
        assertEquals(8L, rootLesson.getParentId());
        assertTrue(sub.getDeletedTime() != null);
        assertTrue(target.getDeletedTime() == null);
    }

    @Test
    void updateSubcourse_updatesTitleAndCover() {
        CourseEntity course = course();
        CourseCatalogNodeEntity sub = new CourseCatalogNodeEntity();
        sub.setId(5L);
        sub.setCourseId(course.getId());
        sub.setNodeType(CourseCatalogNodeType.SUBCOURSE);
        sub.setParentId(0L);
        sub.setTitle("旧名");
        sub.setCoverMaterialId("cover-old");
        sub.setCoverUrl("https://cdn.example/old.jpg");
        when(catalogRepository.findByIdAndCourseIdAndDeletedTimeIsNull(5L, course.getId()))
                .thenReturn(Optional.of(sub));
        when(materialRepository.findById("cover-1")).thenReturn(Optional.of(cover("cover-1")));
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateSubcourseRequest body = new UpdateSubcourseRequest();
        body.setId(5L);
        body.setTitle("新名");
        body.setCoverMaterialId("cover-1");

        CatalogNodeView view = catalogService.updateSubcourse(course.getId(), body);

        assertEquals(5L, view.getId());
        assertEquals("新名", view.getTitle());
        assertEquals("cover-1", view.getCoverMaterialId());
        assertEquals("https://cdn.example/cover.jpg", view.getCoverUrl());
    }

    @Test
    void updateChapter_updatesTitle() {
        CourseEntity course = course();
        CourseCatalogNodeEntity chapter = new CourseCatalogNodeEntity();
        chapter.setId(7L);
        chapter.setCourseId(course.getId());
        chapter.setNodeType(CourseCatalogNodeType.CHAPTER);
        chapter.setParentId(0L);
        chapter.setTitle("旧章节");
        when(catalogRepository.findByIdAndCourseIdAndDeletedTimeIsNull(7L, course.getId()))
                .thenReturn(Optional.of(chapter));
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        UpdateChapterRequest body = new UpdateChapterRequest();
        body.setId(7L);
        body.setTitle("新章节");

        CatalogNodeView view = catalogService.updateChapter(course.getId(), body);

        assertEquals(7L, view.getId());
        assertEquals("新章节", view.getTitle());
    }

    @Test
    void deleteChapter_keepContents_onlySoftDeletesChapter() {
        CourseEntity course = course();
        CourseCatalogNodeEntity chapter = new CourseCatalogNodeEntity();
        chapter.setId(7L);
        chapter.setCourseId(course.getId());
        chapter.setNodeType(CourseCatalogNodeType.CHAPTER);
        chapter.setParentId(0L);
        when(catalogRepository.findByIdAndCourseIdAndDeletedTimeIsNull(7L, course.getId()))
                .thenReturn(Optional.of(chapter));
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeleteChapterRequest body = new DeleteChapterRequest();
        body.setId(7L);
        body.setKeepContents(true);

        CatalogNodeIdResponse response = catalogService.deleteChapter(course.getId(), body);

        assertEquals(7L, response.getId());
        assertTrue(chapter.getDeletedTime() != null);
        verify(catalogRepository, never()).findActiveByCourseIdAndParentId(any(), any(Long.class));
    }

    @Test
    void deleteChapter_keepContents_withTarget_reparentsLessons() {
        CourseEntity course = course();
        CourseCatalogNodeEntity chapter = new CourseCatalogNodeEntity();
        chapter.setId(7L);
        chapter.setCourseId(course.getId());
        chapter.setNodeType(CourseCatalogNodeType.CHAPTER);
        chapter.setParentId(5L);
        CourseCatalogNodeEntity target = new CourseCatalogNodeEntity();
        target.setId(9L);
        target.setCourseId(course.getId());
        target.setNodeType(CourseCatalogNodeType.CHAPTER);
        target.setParentId(5L);
        CourseCatalogNodeEntity lesson = new CourseCatalogNodeEntity();
        lesson.setId(8L);
        lesson.setCourseId(course.getId());
        lesson.setNodeType(CourseCatalogNodeType.LESSON);
        lesson.setParentId(7L);
        when(catalogRepository.findByIdAndCourseIdAndDeletedTimeIsNull(7L, course.getId()))
                .thenReturn(Optional.of(chapter));
        when(catalogRepository.findByIdAndCourseIdAndDeletedTimeIsNull(9L, course.getId()))
                .thenReturn(Optional.of(target));
        when(catalogRepository.findActiveByCourseIdAndParentId(course.getId(), 7L))
                .thenReturn(List.of(lesson));
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeleteChapterRequest body = new DeleteChapterRequest();
        body.setId(7L);
        body.setKeepContents(true);
        body.setTargetChapterId(9L);

        CatalogNodeIdResponse response = catalogService.deleteChapter(course.getId(), body);

        assertEquals(7L, response.getId());
        assertEquals(9L, lesson.getParentId());
        assertTrue(chapter.getDeletedTime() != null);
        assertTrue(target.getDeletedTime() == null);
        assertTrue(lesson.getDeletedTime() == null);
    }

    @Test
    void deleteChapter_cascadeSoftDeletesLessonsAndSyncsContentCount() {
        CourseEntity course = course();
        course.setContentCount(2);
        CourseCatalogNodeEntity chapter = new CourseCatalogNodeEntity();
        chapter.setId(7L);
        chapter.setCourseId(course.getId());
        chapter.setNodeType(CourseCatalogNodeType.CHAPTER);
        chapter.setParentId(0L);
        CourseCatalogNodeEntity lesson = new CourseCatalogNodeEntity();
        lesson.setId(8L);
        lesson.setCourseId(course.getId());
        lesson.setNodeType(CourseCatalogNodeType.LESSON);
        lesson.setParentId(7L);
        when(catalogRepository.findByIdAndCourseIdAndDeletedTimeIsNull(7L, course.getId()))
                .thenReturn(Optional.of(chapter));
        when(catalogRepository.findActiveByCourseIdAndParentId(course.getId(), 7L))
                .thenReturn(List.of(lesson));
        when(catalogRepository.countActiveByCourseIdAndNodeType(course.getId(), CourseCatalogNodeType.LESSON))
                .thenReturn(0L);
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(courseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DeleteChapterRequest body = new DeleteChapterRequest();
        body.setId(7L);
        body.setKeepContents(false);

        CatalogNodeIdResponse response = catalogService.deleteChapter(course.getId(), body);

        assertEquals(7L, response.getId());
        assertTrue(chapter.getDeletedTime() != null);
        assertTrue(lesson.getDeletedTime() != null);
        assertEquals(0, course.getContentCount());
    }

    @Test
    void createChapter_underSubcourse() {
        CourseEntity course = course();
        CourseCatalogNodeEntity sub = new CourseCatalogNodeEntity();
        sub.setId(5L);
        sub.setCourseId(course.getId());
        sub.setNodeType(CourseCatalogNodeType.SUBCOURSE);
        sub.setParentId(0L);
        when(catalogRepository.countActiveByCourseIdAndNodeType(course.getId(), CourseCatalogNodeType.SUBCOURSE))
                .thenReturn(1L);
        when(catalogRepository.findByIdAndCourseIdAndDeletedTimeIsNull(5L, course.getId()))
                .thenReturn(Optional.of(sub));
        when(catalogRepository.findActiveByCourseIdAndParentId(course.getId(), 5L)).thenReturn(List.of());
        when(catalogRepository.save(any())).thenAnswer(inv -> {
            CourseCatalogNodeEntity n = inv.getArgument(0);
            n.setId(7L);
            return n;
        });

        CreateChapterRequest body = new CreateChapterRequest();
        body.setParentId(5L);
        body.setTitle("章节1");

        CatalogNodeView view = catalogService.createChapter(course.getId(), body);
        assertEquals(7L, view.getId());
        assertEquals(CourseCatalogNodeType.CHAPTER, view.getNodeType());

        ArgumentCaptor<CourseCatalogNodeEntity> saved = ArgumentCaptor.forClass(CourseCatalogNodeEntity.class);
        verify(catalogRepository).save(saved.capture());
        assertEquals(5L, saved.getValue().getParentId());
        assertEquals(0, saved.getValue().getSortOrder());
    }

    @Test
    void reorderCatalog_chaptersMode_onlyReordersChapters() {
        CourseEntity course = course();
        CourseCatalogNodeEntity lesson = new CourseCatalogNodeEntity();
        lesson.setId(1L);
        lesson.setCourseId(course.getId());
        lesson.setParentId(0L);
        lesson.setNodeType(CourseCatalogNodeType.LESSON);
        lesson.setSortOrder(0);
        CourseCatalogNodeEntity chapterA = new CourseCatalogNodeEntity();
        chapterA.setId(2L);
        chapterA.setCourseId(course.getId());
        chapterA.setParentId(0L);
        chapterA.setNodeType(CourseCatalogNodeType.CHAPTER);
        chapterA.setSortOrder(0);
        CourseCatalogNodeEntity chapterB = new CourseCatalogNodeEntity();
        chapterB.setId(3L);
        chapterB.setCourseId(course.getId());
        chapterB.setParentId(0L);
        chapterB.setNodeType(CourseCatalogNodeType.CHAPTER);
        chapterB.setSortOrder(0);
        when(catalogRepository.findActiveByCourseId(course.getId()))
                .thenReturn(List.of(lesson, chapterA, chapterB));
        when(catalogRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReorderCatalogRequest body = new ReorderCatalogRequest();
        body.setParentId(0L);
        body.setMode("chapters");
        ReorderCatalogItem a = new ReorderCatalogItem();
        a.setId(3L);
        a.setParentId(0L);
        ReorderCatalogItem b = new ReorderCatalogItem();
        b.setId(2L);
        b.setParentId(0L);
        body.setItems(List.of(a, b));

        catalogService.reorderCatalog(course.getId(), body);

        assertEquals(0, lesson.getSortOrder());
        assertEquals(1, chapterB.getSortOrder());
        assertEquals(2, chapterA.getSortOrder());
    }

    private CourseEntity course() {
        CourseEntity entity = new CourseEntity();
        entity.setId(UuidIds.newId());
        entity.setContentCount(0);
        when(courseRepository.findById(entity.getId())).thenReturn(Optional.of(entity));
        return entity;
    }

    private static MaterialEntity cover(String id) {
        MaterialEntity material = new MaterialEntity();
        material.setId(id);
        material.setType("image");
        material.setName("cover.jpg");
        material.setUrl("https://cdn.example/cover.jpg");
        material.setMimeType("image/jpeg");
        material.setFileSize(1024L);
        material.setAiAuditStatus(1);
        material.setRiskAuditStatus(1);
        return material;
    }
}
