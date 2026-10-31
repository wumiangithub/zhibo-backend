package com.zhibo.admin.course;

import com.zhibo.course.CourseCatalogNodeEntity;
import com.zhibo.course.CourseCatalogNodeType;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CatalogSiblingOrderTest {

    @Test
    void defaultOrder_chaptersBeforeLessons_thenCreatedTime() {
        CourseCatalogNodeEntity lessonOld = node(1L, CourseCatalogNodeType.LESSON, 0,
                LocalDateTime.of(2024, 1, 1, 0, 0));
        CourseCatalogNodeEntity chapterNew = node(2L, CourseCatalogNodeType.CHAPTER, 0,
                LocalDateTime.of(2024, 6, 1, 0, 0));
        CourseCatalogNodeEntity lessonNew = node(3L, CourseCatalogNodeType.LESSON, 0,
                LocalDateTime.of(2024, 3, 1, 0, 0));
        CourseCatalogNodeEntity chapterOld = node(4L, CourseCatalogNodeType.CHAPTER, 0,
                LocalDateTime.of(2024, 2, 1, 0, 0));

        List<CourseCatalogNodeEntity> siblings = new ArrayList<>(List.of(
                lessonOld, chapterNew, lessonNew, chapterOld));
        CatalogSiblingOrder.sortSiblings(siblings);

        assertEquals(List.of(4L, 2L, 1L, 3L), siblings.stream().map(CourseCatalogNodeEntity::getId).toList());
    }

    @Test
    void customOrder_interleavedBySortOrder_whenLessonHasCustom() {
        CourseCatalogNodeEntity lesson = node(1L, CourseCatalogNodeType.LESSON, 1, null);
        CourseCatalogNodeEntity chapter = node(2L, CourseCatalogNodeType.CHAPTER, 2, null);
        CourseCatalogNodeEntity lesson2 = node(3L, CourseCatalogNodeType.LESSON, 3, null);

        List<CourseCatalogNodeEntity> siblings = new ArrayList<>(List.of(chapter, lesson2, lesson));
        assertFalse(CatalogSiblingOrder.isDefaultOrder(siblings));
        assertTrue(CatalogSiblingOrder.usesInterleavedOrder(siblings));
        CatalogSiblingOrder.sortSiblings(siblings);

        assertEquals(List.of(1L, 2L, 3L), siblings.stream().map(CourseCatalogNodeEntity::getId).toList());
    }

    @Test
    void chapterOnlyCustom_keepsTypeGroupBeforeLessons() {
        CourseCatalogNodeEntity lesson = node(1L, CourseCatalogNodeType.LESSON, 0,
                LocalDateTime.of(2024, 1, 1, 0, 0));
        CourseCatalogNodeEntity chapterB = node(2L, CourseCatalogNodeType.CHAPTER, 2, null);
        CourseCatalogNodeEntity chapterA = node(3L, CourseCatalogNodeType.CHAPTER, 1, null);

        List<CourseCatalogNodeEntity> siblings = new ArrayList<>(List.of(lesson, chapterB, chapterA));
        assertFalse(CatalogSiblingOrder.usesInterleavedOrder(siblings));
        CatalogSiblingOrder.sortSiblings(siblings);

        assertEquals(List.of(3L, 2L, 1L), siblings.stream().map(CourseCatalogNodeEntity::getId).toList());
    }

    @Test
    void nextSortOrder_staysZeroWhenUnsorted() {
        List<CourseCatalogNodeEntity> siblings = List.of(
                node(1L, CourseCatalogNodeType.CHAPTER, 0, null),
                node(2L, CourseCatalogNodeType.LESSON, 0, null));
        assertEquals(0, CatalogSiblingOrder.nextSortOrder(siblings));
    }

    @Test
    void nextSortOrder_appendsAfterCustom() {
        List<CourseCatalogNodeEntity> siblings = List.of(
                node(1L, CourseCatalogNodeType.LESSON, 1, null),
                node(2L, CourseCatalogNodeType.CHAPTER, 2, null));
        assertEquals(3, CatalogSiblingOrder.nextSortOrder(siblings));
    }

    @Test
    void applyExplicitOrder_writesOneBased() {
        List<CourseCatalogNodeEntity> ordered = new ArrayList<>(List.of(
                node(10L, CourseCatalogNodeType.LESSON, 0, null),
                node(11L, CourseCatalogNodeType.CHAPTER, 0, null)));
        CatalogSiblingOrder.applyExplicitOrder(ordered);
        assertEquals(1, ordered.get(0).getSortOrder());
        assertEquals(2, ordered.get(1).getSortOrder());
        assertTrue(CatalogSiblingOrder.sameIdSet(List.of(10L, 11L), List.of(11L, 10L)));
    }

    private static CourseCatalogNodeEntity node(
            long id, int type, int sortOrder, LocalDateTime created) {
        CourseCatalogNodeEntity n = new CourseCatalogNodeEntity();
        n.setId(id);
        n.setNodeType(type);
        n.setSortOrder(sortOrder);
        n.setCreatedTime(created);
        n.setParentId(0L);
        return n;
    }
}
