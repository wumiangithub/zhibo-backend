package com.zhibo.admin.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.admin.viewer.dto.LearningCourseListResponse;
import com.zhibo.admin.viewer.dto.LearningSummaryListResponse;
import com.zhibo.admin.viewer.dto.UpdateViewerBlacklistResponse;
import com.zhibo.admin.viewer.dto.UpdateViewerTagsResponse;
import com.zhibo.admin.viewer.dto.ViewerListResponse;
import com.zhibo.course.CourseCatalogContentKind;
import com.zhibo.course.CourseCatalogNodeEntity;
import com.zhibo.course.CourseCatalogNodeRepository;
import com.zhibo.course.CourseLessonProgressEntity;
import com.zhibo.course.CourseLessonProgressRepository;
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialRepository;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRegisterChannel;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerStatus;
import com.zhibo.viewer.ViewerTagEntity;
import com.zhibo.viewer.ViewerTagMemberEntity;
import com.zhibo.viewer.ViewerTagMemberRepository;
import com.zhibo.viewer.ViewerTagRepository;
import com.zhibo.viewer.ViewerTagType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class ViewerAdminServiceTest {

    private ViewerRepository viewerRepository;
    private ViewerTagMemberRepository memberRepository;
    private ViewerTagRepository tagRepository;
    private CourseLessonProgressRepository progressRepository;
    private CourseCatalogNodeRepository catalogNodeRepository;
    private MaterialRepository materialRepository;
    private ViewerAdminService service;

    @BeforeEach
    void setUp() {
        viewerRepository = mock(ViewerRepository.class);
        memberRepository = mock(ViewerTagMemberRepository.class);
        tagRepository = mock(ViewerTagRepository.class);
        progressRepository = mock(CourseLessonProgressRepository.class);
        catalogNodeRepository = mock(CourseCatalogNodeRepository.class);
        materialRepository = mock(MaterialRepository.class);
        service = new ViewerAdminService(
                viewerRepository,
                memberRepository,
                tagRepository,
                progressRepository,
                catalogNodeRepository,
                materialRepository);
    }

    @Test
    void list_mapsFieldsAndPlaceholders() {
        ViewerEntity row = baseViewer(3L);
        row.setNickname("阿坤");
        row.setPhone("18888888888");
        row.setAvatarUrl("https://cdn/a.jpg");
        row.setLastVisitTime(LocalDateTime.of(2026, 10, 27, 10, 0, 0));
        row.setBanComment(1);
        when(viewerRepository.findAll(ArgumentMatchers.<Specification<ViewerEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));
        when(memberRepository.findByViewerIdIn(List.of(3L))).thenReturn(List.of());

        ViewerListResponse res = service.list(1, 20, null, null, null, null, null, null, null, null, null, null);
        assertEquals(1, res.getTotal());
        assertEquals(3L, res.getList().get(0).getId());
        assertEquals("阿坤", res.getList().get(0).getNickname());
        assertEquals("188****8888", res.getList().get(0).getPhoneMasked());
        assertEquals(ViewerRegisterChannel.WECHAT, res.getList().get(0).getRegisterChannel());
        assertEquals(0, res.getList().get(0).getConsumeCount());
        assertEquals("0.00", res.getList().get(0).getConsumeAmount());
        assertEquals("2026-10-27 10:00:00", res.getList().get(0).getLastVisitTime());
        assertEquals(1, res.getList().get(0).getBanComment());
        assertEquals(0, res.getList().get(0).getBanAccess());
    }

    @Test
    void list_defaultNickname_noPhoneUsesId() {
        ViewerEntity row = baseViewer(8L);
        row.setNickname("  ");
        when(viewerRepository.findAll(ArgumentMatchers.<Specification<ViewerEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));
        when(memberRepository.findByViewerIdIn(List.of(8L))).thenReturn(List.of());

        ViewerListResponse res = service.list(1, 10, null, null, null, null, null, null, null, null, null, null);
        assertEquals("用户_8", res.getList().get(0).getNickname());
    }

    @Test
    void list_defaultNickname_withPhoneUsesTail() {
        ViewerEntity row = baseViewer(8L);
        row.setNickname(null);
        row.setPhone("13800138000");
        when(viewerRepository.findAll(ArgumentMatchers.<Specification<ViewerEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row)));
        when(memberRepository.findByViewerIdIn(List.of(8L))).thenReturn(List.of());

        ViewerListResponse res = service.list(1, 10, null, null, null, null, null, null, null, null, null, null);
        assertEquals("用户8000", res.getList().get(0).getNickname());
    }

    @Test
    void learnDurationFilterMiss_gtEmpty_lteKeep() {
        assertTrue(ViewerAdminService.learnDurationFilterMiss("gt", 0));
        assertTrue(ViewerAdminService.learnDurationFilterMiss("gt", 60));
        assertFalse(ViewerAdminService.learnDurationFilterMiss("lte", 0));
        assertFalse(ViewerAdminService.learnDurationFilterMiss("lte", 100));
        assertFalse(ViewerAdminService.learnDurationFilterMiss(null, null));
        assertThrows(IllegalArgumentException.class, () -> ViewerAdminService.learnDurationFilterMiss("gt", null));
        assertThrows(IllegalArgumentException.class, () -> ViewerAdminService.learnDurationFilterMiss(null, 1));
    }

    @Test
    void learningSummary_gtDuration_empty() {
        LearningSummaryListResponse res = service.learningSummary(
                1, 10, null, null, null, null, "gt", 1, null, null);
        assertEquals(0, res.getTotal());
        assertTrue(res.getList().isEmpty());
    }

    @Test
    void learningCourses_mapsLessonRow() {
        CourseLessonProgressEntity progress = new CourseLessonProgressEntity();
        progress.setId(9L);
        progress.setCourseId("c1");
        progress.setLessonNodeId(18L);
        progress.setViewerId(3L);
        progress.setLearnDurationSec(1800L);
        progress.setProgressPercent(84);
        progress.setFirstParticipateTime(LocalDateTime.of(2026, 9, 22, 17, 0, 0));
        when(progressRepository.findAll(
                        ArgumentMatchers.<Specification<CourseLessonProgressEntity>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(progress)));

        ViewerEntity viewer = baseViewer(3L);
        viewer.setNickname("阿坤");
        viewer.setAvatarUrl("https://cdn/a.jpg");
        when(viewerRepository.findAllById(ArgumentMatchers.<Iterable<Long>>any())).thenReturn(List.of(viewer));
        when(memberRepository.findByViewerIdIn(List.of(3L))).thenReturn(List.of());

        CourseCatalogNodeEntity node = new CourseCatalogNodeEntity();
        node.setId(18L);
        node.setTitle("第十八节 心态修炼");
        node.setContentKind(CourseCatalogContentKind.VIDEO);
        node.setContentId("mat1");
        when(catalogNodeRepository.findAllById(ArgumentMatchers.<Iterable<Long>>any())).thenReturn(List.of(node));

        MaterialEntity material = new MaterialEntity();
        material.setId("mat1");
        material.setMeta("30:00");
        when(materialRepository.findAllById(ArgumentMatchers.<Iterable<String>>any())).thenReturn(List.of(material));

        LearningCourseListResponse res = service.learningCourses(1, 10, null, null, null, null);
        assertEquals(1, res.getTotal());
        assertEquals(9L, res.getList().get(0).getId());
        assertEquals(3L, res.getList().get(0).getViewerId());
        assertEquals("阿坤", res.getList().get(0).getNickname());
        assertEquals(18L, res.getList().get(0).getLessonNodeId());
        assertEquals("第十八节 心态修炼", res.getList().get(0).getLessonName());
        assertEquals(1800, res.getList().get(0).getTotalLearnDurationSec());
        assertEquals(84, res.getList().get(0).getProgressPercent());
        assertEquals("2026-09-22 17:00:00", res.getList().get(0).getFirstLearnTime());
        assertEquals("视频", res.getList().get(0).getLessonType());
        assertEquals(1800, res.getList().get(0).getLessonDurationSec());
    }

    @Test
    void learningCourses_keywordNoMatch_empty() {
        when(viewerRepository.findAll(ArgumentMatchers.<Specification<ViewerEntity>>any()))
                .thenReturn(List.of());
        LearningCourseListResponse res = service.learningCourses(1, 10, "nickname", "nobody", null, null);
        assertEquals(0, res.getTotal());
        assertTrue(res.getList().isEmpty());
    }

    @Test
    void parseDurationMetaToSec_mmSsAndHms() {
        assertEquals(1800, ViewerAdminService.parseDurationMetaToSec("30:00"));
        assertEquals(3723, ViewerAdminService.parseDurationMetaToSec("1:02:03"));
        assertEquals(0, ViewerAdminService.parseDurationMetaToSec("bad"));
        assertEquals(0, ViewerAdminService.parseDurationMetaToSec(null));
    }

    @Test
    void displayNickname_rules() {
        ViewerEntity withNick = baseViewer(1L);
        withNick.setNickname("阿坤");
        withNick.setPhone("13800138000");
        assertEquals("阿坤", ViewerAdminService.displayNickname(withNick));

        ViewerEntity phoneOnly = baseViewer(2L);
        phoneOnly.setPhone("13612348079");
        assertEquals("用户8079", ViewerAdminService.displayNickname(phoneOnly));

        ViewerEntity neither = baseViewer(3L);
        assertEquals("用户_3", ViewerAdminService.displayNickname(neither));
    }

    @Test
    void list_emptyWhenConsumeMinPositive() {
        ViewerListResponse res = service.list(1, 20, null, null, 1.0, null, null, null, null, null, null, null);
        assertEquals(0, res.getTotal());
        assertTrue(res.getList().isEmpty());
    }

    @Test
    void list_emptyWhenIdKeywordInvalid() {
        ViewerListResponse res = service.list(1, 20, "id", "abc", null, null, null, null, null, null, null, null);
        assertEquals(0, res.getTotal());
    }

    @Test
    void normalizeFilterTagIds_skipsInvalid() {
        java.util.ArrayList<Long> raw = new java.util.ArrayList<>();
        raw.add(1L);
        raw.add(null);
        raw.add(0L);
        raw.add(3L);
        raw.add(1L);
        assertEquals(List.of(1L, 3L), ViewerAdminService.normalizeFilterTagIds(raw));
    }

    @Test
    void updateBlacklist_syncsStatus() {
        ViewerEntity row = baseViewer(5L);
        when(viewerRepository.findById(5L)).thenReturn(Optional.of(row));
        when(viewerRepository.save(any(ViewerEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateViewerBlacklistResponse res = service.updateBlacklist(5L, 1, 0, 1);
        assertEquals(1, res.getBanComment());
        assertEquals(0, res.getBanPurchase());
        assertEquals(1, res.getBanAccess());
        assertEquals(ViewerStatus.DISABLED, res.getStatus());
        assertEquals(ViewerStatus.DISABLED, row.getStatus());
    }

    @Test
    void updateTags_replacesManualOnly() {
        ViewerEntity row = baseViewer(9L);
        when(viewerRepository.findById(9L)).thenReturn(Optional.of(row));

        ViewerTagEntity manualOld = tag(1L, "A", ViewerTagType.MANUAL);
        ViewerTagEntity auto = tag(2L, "Auto", ViewerTagType.AUTO);
        ViewerTagEntity manualNew = tag(3L, "B", ViewerTagType.MANUAL);

        ViewerTagMemberEntity m1 = member(1L, 9L, 1L);
        ViewerTagMemberEntity m2 = member(2L, 9L, 2L);
        ViewerTagMemberEntity m3 = member(3L, 9L, 3L);
        when(memberRepository.findByViewerId(9L)).thenReturn(List.of(m1, m2), List.of(m2, m3));
        when(tagRepository.findAllById(ArgumentMatchers.anyCollection()))
                .thenAnswer(inv -> {
                    @SuppressWarnings("unchecked")
                    java.util.Collection<Long> ids = inv.getArgument(0);
                    List<ViewerTagEntity> all = List.of(manualOld, auto, manualNew);
                    return all.stream().filter(t -> ids.contains(t.getId())).toList();
                });
        when(memberRepository.save(any(ViewerTagMemberEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdateViewerTagsResponse res = service.updateTags(9L, List.of(3L));
        verify(memberRepository).delete(m1);
        assertEquals(List.of(2L, 3L), res.getTagIds());
        assertEquals(List.of("Auto", "B"), res.getTagLabels());
    }

    @Test
    void placeholderFilterMiss_logic() {
        assertTrue(ViewerAdminService.placeholderFilterMiss(0.01, null, null, null));
        assertFalse(ViewerAdminService.placeholderFilterMiss(0.0, 100.0, 0, 10));
        assertTrue(ViewerAdminService.placeholderFilterMiss(null, null, 1, null));
    }

    @Test
    void maskPhone_elevenDigits() {
        assertEquals("136****8079", ViewerAdminService.maskPhone("13612348079"));
    }

    private static ViewerEntity baseViewer(long id) {
        ViewerEntity row = new ViewerEntity();
        row.setId(id);
        row.setRegisterChannel(ViewerRegisterChannel.WECHAT);
        row.setStatus(ViewerStatus.NORMAL);
        row.setBanComment(0);
        row.setBanPurchase(0);
        row.setBanAccess(0);
        row.setCreatedTime(LocalDateTime.of(2026, 10, 1, 12, 0, 0));
        row.setLastLoginTime(LocalDateTime.of(2026, 10, 1, 12, 0, 0));
        return row;
    }

    private static ViewerTagEntity tag(long id, String name, int type) {
        ViewerTagEntity t = new ViewerTagEntity();
        t.setId(id);
        t.setName(name);
        t.setType(type);
        t.setGroupId(1L);
        return t;
    }

    private static ViewerTagMemberEntity member(long id, long viewerId, long tagId) {
        ViewerTagMemberEntity m = new ViewerTagMemberEntity();
        m.setId(id);
        m.setViewerId(viewerId);
        m.setTagId(tagId);
        return m;
    }
}
