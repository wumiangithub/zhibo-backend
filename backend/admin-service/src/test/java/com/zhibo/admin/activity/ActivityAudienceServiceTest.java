package com.zhibo.admin.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityAudienceEntity;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.ActivityCommentRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerTagEntity;
import com.zhibo.viewer.ViewerTagMemberEntity;
import com.zhibo.viewer.ViewerTagMemberRepository;
import com.zhibo.viewer.ViewerTagRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityAudienceServiceTest {

    private static final String ACTIVITY_ID = "b".repeat(32);

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivityAudienceRepository audienceRepository;
    @Mock
    private ActivityCommentRepository commentRepository;
    @Mock
    private ViewerRepository viewerRepository;
    @Mock
    private ViewerTagMemberRepository viewerTagMemberRepository;
    @Mock
    private ViewerTagRepository viewerTagRepository;
    @Mock
    private SysUserAccountRepository userRepository;

    private ActivityAudienceService service;

    @BeforeEach
    void setUp() {
        service = new ActivityAudienceService(
                activityRepository,
                audienceRepository,
                commentRepository,
                viewerRepository,
                viewerTagMemberRepository,
                viewerTagRepository,
                userRepository);
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID);
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
    }

    @Test
    void list_showsEmployeeInviter() {
        ActivityAudienceEntity row = new ActivityAudienceEntity();
        row.setId(1L);
        row.setActivityId(ACTIVITY_ID);
        row.setViewerId(10L);
        row.setEntitlementStatus(1);
        row.setEmployeeInviterId(100L);

        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(10L);
        viewer.setNickname("wyh");

        SysUserAccountEntity emp = new SysUserAccountEntity();
        emp.setId(100L);
        emp.setUserName("王富贵");
        emp.setRsStatus(1);

        when(audienceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(row));
        when(commentRepository.countActiveByViewerIds(any(), any())).thenReturn(List.of());
        when(viewerRepository.findByIdIn(any())).thenReturn(List.of(viewer));
        when(viewerTagMemberRepository.findByViewerIdIn(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(emp));

        var resp = service.list(ACTIVITY_ID, 1, 20, null, null, null, null, null, null, null, null, null, null, null);
        assertEquals(1, resp.getList().size());
        assertEquals("employee", resp.getList().get(0).getInviterKind());
        assertEquals("王富贵", resp.getList().get(0).getInviterDisplayName());
        assertTrue(resp.getList().get(0).getTagIds().isEmpty());
        assertTrue(resp.getList().get(0).getTagLabels().isEmpty());
    }

    @Test
    void list_emptyInviterWhenOnlyAudienceInviteOrImport() {
        ActivityAudienceEntity row = new ActivityAudienceEntity();
        row.setId(2L);
        row.setActivityId(ACTIVITY_ID);
        row.setViewerId(10L);
        row.setEntitlementStatus(1);
        row.setImporterId(200L);

        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(10L);
        viewer.setNickname("wyh");

        SysUserAccountEntity importer = new SysUserAccountEntity();
        importer.setId(200L);
        importer.setUserName("导入员");
        importer.setRsStatus(1);

        when(audienceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(row));
        when(commentRepository.countActiveByViewerIds(any(), any())).thenReturn(List.of());
        when(viewerRepository.findByIdIn(any())).thenReturn(List.of(viewer));
        when(viewerTagMemberRepository.findByViewerIdIn(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(importer));

        var resp = service.list(ACTIVITY_ID, 1, 20, null, null, null, null, null, null, null, null, null, null, null);
        assertNull(resp.getList().get(0).getInviterKind());
        assertNull(resp.getList().get(0).getInviterDisplayName());
        assertEquals("导入员", resp.getList().get(0).getImporterName());
    }

    @Test
    void list_fillsViewerTags() {
        ActivityAudienceEntity row = new ActivityAudienceEntity();
        row.setId(3L);
        row.setActivityId(ACTIVITY_ID);
        row.setViewerId(10L);
        row.setEntitlementStatus(1);

        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(10L);
        viewer.setNickname("wyh");

        ViewerTagMemberEntity member = new ViewerTagMemberEntity();
        member.setId(1L);
        member.setViewerId(10L);
        member.setTagId(7L);

        ViewerTagEntity tag = new ViewerTagEntity();
        tag.setId(7L);
        tag.setName("高意向");

        when(audienceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(row));
        when(commentRepository.countActiveByViewerIds(any(), any())).thenReturn(List.of());
        when(viewerRepository.findByIdIn(any())).thenReturn(List.of(viewer));
        when(viewerTagMemberRepository.findByViewerIdIn(any())).thenReturn(List.of(member));
        when(viewerTagRepository.findAllById(any())).thenReturn(List.of(tag));

        var resp = service.list(ACTIVITY_ID, 1, 20, null, null, null, null, null, null, null, null, null, null, null);
        assertEquals(List.of(7L), resp.getList().get(0).getTagIds());
        assertEquals(List.of("高意向"), resp.getList().get(0).getTagLabels());
    }

    @Test
    void list_invalidKeywordId_returnsEmpty() {
        var resp = service.list(ACTIVITY_ID, 1, 20, "id", "abc", null, null, null, null, null, null, null, null, null);
        assertEquals(0, resp.getTotal());
        assertTrue(resp.getList().isEmpty());
    }

    @Test
    void list_totalWatchGteOne_filtersOutPlaceholderZero() {
        ActivityAudienceEntity row = new ActivityAudienceEntity();
        row.setId(4L);
        row.setActivityId(ACTIVITY_ID);
        row.setViewerId(10L);
        row.setEntitlementStatus(1);

        when(audienceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(row));

        var resp = service.list(
                ACTIVITY_ID, 1, 20, null, null, null, null, null, "gte", 1L, null, null, null, null);
        assertEquals(0, resp.getTotal());
        assertTrue(resp.getList().isEmpty());
    }

    @Test
    void list_totalWatchLteZero_keepsPlaceholderZero() {
        ActivityAudienceEntity row = new ActivityAudienceEntity();
        row.setId(5L);
        row.setActivityId(ACTIVITY_ID);
        row.setViewerId(10L);
        row.setEntitlementStatus(1);

        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(10L);
        viewer.setNickname("wyh");

        when(audienceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(row));
        when(commentRepository.countActiveByViewerIds(any(), any())).thenReturn(List.of());
        when(viewerRepository.findByIdIn(any())).thenReturn(List.of(viewer));
        when(viewerTagMemberRepository.findByViewerIdIn(any())).thenReturn(List.of());

        var resp = service.list(
                ACTIVITY_ID, 1, 20, null, null, null, null, null, "lte", 0L, null, null, null, null);
        assertEquals(1, resp.getTotal());
        assertEquals(0L, resp.getList().get(0).getTotalWatchDurationSec());
    }
}
