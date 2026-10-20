package com.zhibo.admin.org;

import com.zhibo.admin.org.dto.RoleMenuNode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveMenuCatalogTest {

    @Test
    void liveGroupOrder_putsReportCreateFirst() {
        RoleMenuNode live = LiveMenuCatalog.tree().get(1);
        assertEquals("live", live.getCode());
        assertEquals(
                List.of("live-report-create", "live-report", "live-create", "live-manage", "live-console"),
                live.getChildren().stream().map(RoleMenuNode::getCode).toList());
    }

    @Test
    void courseGroup_followsLive() {
        assertEquals("live", LiveMenuCatalog.tree().get(1).getCode());
        RoleMenuNode course = LiveMenuCatalog.tree().get(2);
        assertEquals("course", course.getCode());
        assertEquals(
                List.of("course-create", "course-manage"),
                course.getChildren().stream().map(RoleMenuNode::getCode).toList());
        assertTrue(LiveMenuCatalog.known("course-create"));
        assertTrue(LiveMenuCatalog.known("course-manage"));
    }

    @Test
    void known_includesReportCreate() {
        assertTrue(LiveMenuCatalog.known("live-report-create"));
        assertTrue(LiveMenuCatalog.known("live-report"));
    }

    @Test
    void withImplied_pairsReportAndCreate() {
        assertEquals(Set.of("live-report", "live-report-create"),
                new HashSet<>(LiveMenuCatalog.withImplied(List.of("live-report"))));
        assertEquals(Set.of("live-report", "live-report-create"),
                new HashSet<>(LiveMenuCatalog.withImplied(List.of("live-report-create"))));
        assertEquals(List.of("live-manage"), LiveMenuCatalog.withImplied(List.of("live-manage")));
    }
}
