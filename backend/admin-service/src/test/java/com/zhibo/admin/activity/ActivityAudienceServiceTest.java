package com.zhibo.admin.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.activity.ActivityAudienceEntity;
import com.zhibo.activity.ActivityAudienceImportEntity;
import com.zhibo.activity.ActivityAudienceImportRepository;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.activity.dto.CreateAudienceRequest;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerTagEntity;
import com.zhibo.viewer.ViewerTagMemberEntity;
import com.zhibo.viewer.ViewerTagMemberRepository;
import com.zhibo.viewer.ViewerTagRepository;
import com.zhibo.presence.PresenceEventRepository;
import com.zhibo.replay.RoomReplayWatchRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class ActivityAudienceServiceTest {

    private static final String ACTIVITY_ID = "b".repeat(32);

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivityAudienceRepository audienceRepository;
    @Mock
    private RoomCommentRepository commentRepository;
    @Mock
    private ViewerRepository viewerRepository;
    @Mock
    private ViewerTagMemberRepository viewerTagMemberRepository;
    @Mock
    private ViewerTagRepository viewerTagRepository;
    @Mock
    private SysUserAccountRepository userRepository;
    @Mock
    private ActivityAudienceImportRepository importRepository;
    @Mock
    private PresenceEventRepository presenceEventRepository;
    @Mock
    private RoomReplayWatchRepository replayWatchRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private ActivityAudienceService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-29T04:00:00Z"), ZoneOffset.UTC);
        service = new ActivityAudienceService(
                activityRepository,
                audienceRepository,
                commentRepository,
                viewerRepository,
                viewerTagMemberRepository,
                viewerTagRepository,
                userRepository,
                importRepository,
                objectMapper,
                presenceEventRepository,
                replayWatchRepository,
                clock);
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID);
        lenient().when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
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

    @Test
    void create_byPhone_insertsRow() {
        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(10L);
        viewer.setPhone("13800138000");

        SysUserAccountEntity op = new SysUserAccountEntity();
        op.setId(99L);
        op.setRsStatus(1);

        when(viewerRepository.findByPhone("13800138000")).thenReturn(Optional.of(viewer));
        when(userRepository.findById(99L)).thenReturn(Optional.of(op));
        when(audienceRepository.findByActivityIdAndViewerId(ACTIVITY_ID, 10L)).thenReturn(Optional.empty());
        when(audienceRepository.save(any(ActivityAudienceEntity.class))).thenAnswer(inv -> {
            ActivityAudienceEntity row = inv.getArgument(0);
            row.setId(501L);
            return row;
        });

        CreateAudienceRequest req = new CreateAudienceRequest();
        req.setPhone("13800138000");
        var resp = service.create(ACTIVITY_ID, req, 99L);
        assertEquals(501L, resp.getId());
        assertEquals(10L, resp.getViewerId());
        assertTrue(resp.isCreated());

        ArgumentCaptor<ActivityAudienceEntity> cap = ArgumentCaptor.forClass(ActivityAudienceEntity.class);
        verify(audienceRepository).save(cap.capture());
        assertEquals(1, cap.getValue().getEntitlementStatus());
        assertEquals(99L, cap.getValue().getImporterId());
    }

    @Test
    void create_alreadyInAudience_returnsCreatedFalse() {
        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(10L);
        viewer.setPhone("13800138000");

        SysUserAccountEntity op = new SysUserAccountEntity();
        op.setId(99L);
        op.setRsStatus(1);

        ActivityAudienceEntity existing = new ActivityAudienceEntity();
        existing.setId(501L);
        existing.setActivityId(ACTIVITY_ID);
        existing.setViewerId(10L);
        existing.setEntitlementStatus(1);
        existing.setImporterId(88L);

        when(viewerRepository.findByPhone("13800138000")).thenReturn(Optional.of(viewer));
        when(userRepository.findById(99L)).thenReturn(Optional.of(op));
        when(audienceRepository.findByActivityIdAndViewerId(ACTIVITY_ID, 10L))
                .thenReturn(Optional.of(existing));

        CreateAudienceRequest req = new CreateAudienceRequest();
        req.setPhone("13800138000");
        var resp = service.create(ACTIVITY_ID, req, 99L);
        assertEquals(501L, resp.getId());
        assertEquals(10L, resp.getViewerId());
        assertFalse(resp.isCreated());
        verify(audienceRepository, never()).save(any());
    }

    @Test
    void create_byViewerId_insertsRow() {
        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(12L);

        SysUserAccountEntity op = new SysUserAccountEntity();
        op.setId(99L);
        op.setRsStatus(1);

        when(viewerRepository.findById(12L)).thenReturn(Optional.of(viewer));
        when(userRepository.findById(99L)).thenReturn(Optional.of(op));
        when(audienceRepository.findByActivityIdAndViewerId(ACTIVITY_ID, 12L)).thenReturn(Optional.empty());
        when(audienceRepository.save(any(ActivityAudienceEntity.class))).thenAnswer(inv -> {
            ActivityAudienceEntity row = inv.getArgument(0);
            row.setId(502L);
            return row;
        });

        CreateAudienceRequest req = new CreateAudienceRequest();
        req.setViewerId(12L);
        var resp = service.create(ACTIVITY_ID, req, 99L);
        assertEquals(502L, resp.getId());
        assertEquals(12L, resp.getViewerId());
        assertTrue(resp.isCreated());
    }

    @Test
    void create_bothPhoneAndViewerId_rejects() {
        CreateAudienceRequest req = new CreateAudienceRequest();
        req.setPhone("13800138000");
        req.setViewerId(12L);
        assertThrows(IllegalArgumentException.class, () -> service.create(ACTIVITY_ID, req, 99L));
    }

    @Test
    void create_viewerIdNotFound_rejects() {
        when(viewerRepository.findById(eq(999L))).thenReturn(Optional.empty());
        CreateAudienceRequest req = new CreateAudienceRequest();
        req.setViewerId(999L);
        assertThrows(IllegalArgumentException.class, () -> service.create(ACTIVITY_ID, req, 99L));
    }

    @Test
    void importCsv_writesHistory_successCountIncludesCreatedUpdatedSkipped() {
        stubOperator(99L, "导入员");
        stubSaveImport(77L);

        ViewerEntity created = viewer(10L, "13800000001");
        ViewerEntity updated = viewer(11L, "13800000002");
        ViewerEntity skipped = viewer(12L, "13800000003");
        when(viewerRepository.findByPhoneIn(any())).thenReturn(List.of(created, updated, skipped));

        when(audienceRepository.findByActivityIdAndViewerId(ACTIVITY_ID, 10L)).thenReturn(Optional.empty());
        ActivityAudienceEntity existingNoImporter = audienceRow(201L, 11L, null);
        when(audienceRepository.findByActivityIdAndViewerId(ACTIVITY_ID, 11L))
                .thenReturn(Optional.of(existingNoImporter));
        ActivityAudienceEntity existingWithImporter = audienceRow(202L, 12L, 88L);
        when(audienceRepository.findByActivityIdAndViewerId(ACTIVITY_ID, 12L))
                .thenReturn(Optional.of(existingWithImporter));
        when(audienceRepository.save(any(ActivityAudienceEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        MockMultipartFile file = csvFile(
                "手机号\n13800000001\n13800000002\n13800000003\n13900000000\n");
        var resp = service.importCsv(ACTIVITY_ID, file, 99L);
        assertEquals(77L, resp.getImportId());
        assertEquals(4, resp.getTotal());
        assertEquals(1, resp.getCreated());
        assertEquals(1, resp.getUpdated());
        assertEquals(1, resp.getSkipped());
        assertEquals(1, resp.getFailed());
        assertEquals(1, resp.getErrors().size());

        ArgumentCaptor<ActivityAudienceImportEntity> cap =
                ArgumentCaptor.forClass(ActivityAudienceImportEntity.class);
        verify(importRepository).save(cap.capture());
        assertEquals(3, cap.getValue().getSuccessCount());
        assertEquals(1, cap.getValue().getFailCount());
        assertTrue(cap.getValue().getFailDetails().contains("13900000000"));
    }

    @Test
    void importCsv_xlsxRejected() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.xlsx", "application/vnd.ms-excel", "x".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalArgumentException.class, () -> service.importCsv(ACTIVITY_ID, file, 99L));
        verify(importRepository, never()).save(any());
    }

    @Test
    void listImportHistory_newestFirst_fillsImporterName() {
        ActivityAudienceImportEntity newer = new ActivityAudienceImportEntity();
        newer.setId(2L);
        newer.setImporterId(99L);
        newer.setImportTime(LocalDateTime.of(2026, 10, 28, 12, 0));
        newer.setSuccessCount(3);
        newer.setFailCount(1);
        ActivityAudienceImportEntity older = new ActivityAudienceImportEntity();
        older.setId(1L);
        older.setImporterId(99L);
        older.setImportTime(LocalDateTime.of(2026, 10, 27, 12, 0));
        older.setSuccessCount(1);
        older.setFailCount(0);
        when(importRepository.findTop100ByActivityIdOrderByImportTimeDesc(ACTIVITY_ID))
                .thenReturn(List.of(newer, older));
        SysUserAccountEntity op = new SysUserAccountEntity();
        op.setId(99L);
        op.setUserName("导入员");
        when(userRepository.findAllById(any())).thenReturn(List.of(op));

        var resp = service.listImportHistory(ACTIVITY_ID);
        assertEquals(2, resp.getList().size());
        assertEquals(2L, resp.getList().get(0).getId());
        assertEquals("导入员", resp.getList().get(0).getImporterName());
        assertEquals(3, resp.getList().get(0).getSuccessCount());
        assertEquals(1, resp.getList().get(0).getFailCount());
    }

    @Test
    void importFailuresCsv_zeroFail_stillHasHeader() {
        ActivityAudienceImportEntity row = new ActivityAudienceImportEntity();
        row.setId(5L);
        row.setFailCount(0);
        row.setFailDetails("[]");
        when(importRepository.findById(5L)).thenReturn(Optional.of(row));

        String csv = stripBom(service.importFailuresCsv(5L));
        assertEquals("行号,手机号,原因\n", csv);
    }

    @Test
    void exportCsv_includesPhoneAndListFields() {
        ActivityAudienceEntity row = new ActivityAudienceEntity();
        row.setId(1L);
        row.setActivityId(ACTIVITY_ID);
        row.setViewerId(10L);
        row.setEntitlementStatus(1);
        row.setEmployeeInviterId(100L);

        ViewerEntity viewer = viewer(10L, "13800138000");
        viewer.setNickname("wyh");

        SysUserAccountEntity emp = new SysUserAccountEntity();
        emp.setId(100L);
        emp.setUserName("王富贵");

        when(audienceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(row));
        when(commentRepository.countActiveByViewerIds(any(), any())).thenReturn(List.of());
        when(viewerRepository.findByIdIn(any())).thenReturn(List.of(viewer));
        when(viewerTagMemberRepository.findByViewerIdIn(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(emp));

        String csv = stripBom(service.exportCsv(
                ACTIVITY_ID, null, null, null, null, null, null, null, null, null, null, null));
        assertTrue(csv.startsWith("用户,用户ID,手机号,权益状态,所属邀请人,标签"));
        assertTrue(csv.contains("wyh"));
        assertTrue(csv.contains("13800138000"));
        assertTrue(csv.contains("有效"));
        assertTrue(csv.contains("王富贵"));
    }

    private void stubOperator(long id, String name) {
        SysUserAccountEntity op = new SysUserAccountEntity();
        op.setId(id);
        op.setRsStatus(1);
        op.setUserName(name);
        when(userRepository.findById(id)).thenReturn(Optional.of(op));
    }

    private void stubSaveImport(long id) {
        when(importRepository.save(any(ActivityAudienceImportEntity.class))).thenAnswer(inv -> {
            ActivityAudienceImportEntity row = inv.getArgument(0);
            row.setId(id);
            return row;
        });
    }

    private static ViewerEntity viewer(long id, String phone) {
        ViewerEntity v = new ViewerEntity();
        v.setId(id);
        v.setPhone(phone);
        return v;
    }

    private static ActivityAudienceEntity audienceRow(long id, long viewerId, Long importerId) {
        ActivityAudienceEntity row = new ActivityAudienceEntity();
        row.setId(id);
        row.setActivityId(ACTIVITY_ID);
        row.setViewerId(viewerId);
        row.setEntitlementStatus(1);
        row.setImporterId(importerId);
        return row;
    }

    private static MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "a.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private static String stripBom(byte[] raw) {
        String s = new String(raw, StandardCharsets.UTF_8);
        return s.startsWith("\uFEFF") ? s.substring(1) : s;
    }
}
