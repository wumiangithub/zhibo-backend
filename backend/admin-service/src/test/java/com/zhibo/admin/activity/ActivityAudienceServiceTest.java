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
import com.zhibo.admin.activity.dto.ImportAudienceByTagsRequest;
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
    @Mock
    private com.zhibo.activity.ActivityLiveSessionRepository liveSessionRepository;

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
                liveSessionRepository,
                clock);
        lenient().when(liveSessionRepository.findByActivityId(ACTIVITY_ID)).thenReturn(List.of());
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID);
        lenient().when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
    }

    @Test
    void list_showsEmployeeInviter() {
        ActivityAudienceEntity row = new ActivityAudienceEntity();
        row.setId(1L);
        row.setActivityId(ACTIVITY_ID);
        row.setUsername("10");
        row.setEntitlementStatus(1);
        row.setEmployeeInviterId(100L);

        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(10L);
        viewer.setUsername("10");
        viewer.setNickname("wyh");

        SysUserAccountEntity emp = new SysUserAccountEntity();
        emp.setId(100L);
        emp.setUserName("王富贵");
        emp.setRsStatus(1);

        when(audienceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(row));
        when(commentRepository.countActiveByUsernames(any(), any())).thenReturn(List.of());
        when(viewerRepository.findByUsernameIn(any())).thenReturn(List.of(viewer));
        when(viewerTagMemberRepository.findByUsernameIn(any())).thenReturn(List.of());
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
        row.setUsername("10");
        row.setEntitlementStatus(1);
        row.setImporterId(200L);

        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(10L);
        viewer.setUsername("10");
        viewer.setNickname("wyh");

        SysUserAccountEntity importer = new SysUserAccountEntity();
        importer.setId(200L);
        importer.setUserName("导入员");
        importer.setRsStatus(1);

        when(audienceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(row));
        when(commentRepository.countActiveByUsernames(any(), any())).thenReturn(List.of());
        when(viewerRepository.findByUsernameIn(any())).thenReturn(List.of(viewer));
        when(viewerTagMemberRepository.findByUsernameIn(any())).thenReturn(List.of());
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
        row.setUsername("10");
        row.setEntitlementStatus(1);

        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(10L);
        viewer.setUsername("10");
        viewer.setNickname("wyh");

        ViewerTagMemberEntity member = new ViewerTagMemberEntity();
        member.setId(1L);
        member.setUsername("10");
        member.setTagId(7L);

        ViewerTagEntity tag = new ViewerTagEntity();
        tag.setId(7L);
        tag.setName("高意向");

        when(audienceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(row));
        when(commentRepository.countActiveByUsernames(any(), any())).thenReturn(List.of());
        when(viewerRepository.findByUsernameIn(any())).thenReturn(List.of(viewer));
        when(viewerTagMemberRepository.findByUsernameIn(any())).thenReturn(List.of(member));
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
        row.setUsername("10");
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
        row.setUsername("10");
        row.setEntitlementStatus(1);

        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(10L);
        viewer.setUsername("10");
        viewer.setNickname("wyh");

        when(audienceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(row));
        when(commentRepository.countActiveByUsernames(any(), any())).thenReturn(List.of());
        when(viewerRepository.findByUsernameIn(any())).thenReturn(List.of(viewer));
        when(viewerTagMemberRepository.findByUsernameIn(any())).thenReturn(List.of());

        var resp = service.list(
                ACTIVITY_ID, 1, 20, null, null, null, null, null, "lte", 0L, null, null, null, null);
        assertEquals(1, resp.getTotal());
        assertEquals(0L, resp.getList().get(0).getTotalWatchDurationSec());
    }

    @Test
    void create_byPhone_insertsRow() {
        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(10L);
        viewer.setUsername("10");
        viewer.setPhone("13800138000");

        SysUserAccountEntity op = new SysUserAccountEntity();
        op.setId(99L);
        op.setRsStatus(1);

        when(viewerRepository.findByPhone("13800138000")).thenReturn(Optional.of(viewer));
        when(userRepository.findById(99L)).thenReturn(Optional.of(op));
        when(audienceRepository.findByActivityIdAndUsername(ACTIVITY_ID, "10")).thenReturn(Optional.empty());
        when(audienceRepository.save(any(ActivityAudienceEntity.class))).thenAnswer(inv -> {
            ActivityAudienceEntity row = inv.getArgument(0);
            row.setId(501L);
            return row;
        });

        CreateAudienceRequest req = new CreateAudienceRequest();
        req.setPhone("13800138000");
        var resp = service.create(ACTIVITY_ID, req, 99L);
        assertEquals(501L, resp.getId());
        assertEquals("10", resp.getUsername());
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
        viewer.setUsername("10");
        viewer.setPhone("13800138000");

        SysUserAccountEntity op = new SysUserAccountEntity();
        op.setId(99L);
        op.setRsStatus(1);

        ActivityAudienceEntity existing = new ActivityAudienceEntity();
        existing.setId(501L);
        existing.setActivityId(ACTIVITY_ID);
        existing.setUsername("10");
        existing.setEntitlementStatus(1);
        existing.setImporterId(88L);

        when(viewerRepository.findByPhone("13800138000")).thenReturn(Optional.of(viewer));
        when(userRepository.findById(99L)).thenReturn(Optional.of(op));
        when(audienceRepository.findByActivityIdAndUsername(ACTIVITY_ID, "10"))
                .thenReturn(Optional.of(existing));

        CreateAudienceRequest req = new CreateAudienceRequest();
        req.setPhone("13800138000");
        var resp = service.create(ACTIVITY_ID, req, 99L);
        assertEquals(501L, resp.getId());
        assertEquals("10", resp.getUsername());
        assertFalse(resp.isCreated());
        verify(audienceRepository, never()).save(any());
    }

    @Test
    void create_byViewerId_insertsRow() {
        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(12L);
        viewer.setUsername("12");

        SysUserAccountEntity op = new SysUserAccountEntity();
        op.setId(99L);
        op.setRsStatus(1);

        when(viewerRepository.findByUsername("12")).thenReturn(Optional.of(viewer));
        when(userRepository.findById(99L)).thenReturn(Optional.of(op));
        when(audienceRepository.findByActivityIdAndUsername(ACTIVITY_ID, "12")).thenReturn(Optional.empty());
        when(audienceRepository.save(any(ActivityAudienceEntity.class))).thenAnswer(inv -> {
            ActivityAudienceEntity row = inv.getArgument(0);
            row.setId(502L);
            return row;
        });

        CreateAudienceRequest req = new CreateAudienceRequest();
        req.setUsername("12");
        var resp = service.create(ACTIVITY_ID, req, 99L);
        assertEquals(502L, resp.getId());
        assertEquals("12", resp.getUsername());
        assertTrue(resp.isCreated());
    }

    @Test
    void create_bothPhoneAndViewerId_rejects() {
        CreateAudienceRequest req = new CreateAudienceRequest();
        req.setPhone("13800138000");
        req.setUsername("12");
        assertThrows(IllegalArgumentException.class, () -> service.create(ACTIVITY_ID, req, 99L));
    }

    @Test
    void create_viewerIdNotFound_rejects() {
        when(viewerRepository.findByUsername(eq("999"))).thenReturn(Optional.empty());
        CreateAudienceRequest req = new CreateAudienceRequest();
        req.setUsername("999");
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

        when(audienceRepository.findByActivityIdAndUsername(ACTIVITY_ID, "10")).thenReturn(Optional.empty());
        ActivityAudienceEntity existingNoImporter = audienceRow(201L, 11L, null);
        when(audienceRepository.findByActivityIdAndUsername(ACTIVITY_ID, "11"))
                .thenReturn(Optional.of(existingNoImporter));
        ActivityAudienceEntity existingWithImporter = audienceRow(202L, 12L, 88L);
        when(audienceRepository.findByActivityIdAndUsername(ACTIVITY_ID, "12"))
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
        row.setUsername("10");
        row.setEntitlementStatus(1);
        row.setEmployeeInviterId(100L);

        ViewerEntity viewer = viewer(10L, "13800138000");
        viewer.setNickname("wyh");

        SysUserAccountEntity emp = new SysUserAccountEntity();
        emp.setId(100L);
        emp.setUserName("王富贵");

        when(audienceRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class)))
                .thenReturn(List.of(row));
        when(commentRepository.countActiveByUsernames(any(), any())).thenReturn(List.of());
        when(viewerRepository.findByUsernameIn(any())).thenReturn(List.of(viewer));
        when(viewerTagMemberRepository.findByUsernameIn(any())).thenReturn(List.of());
        when(userRepository.findAllById(any())).thenReturn(List.of(emp));

        String csv = stripBom(service.exportCsv(
                ACTIVITY_ID, null, null, null, null, null, null, null, null, null, null, null));
        assertTrue(csv.startsWith("用户,用户ID,手机号,权益状态,所属邀请人,标签"));
        assertTrue(csv.contains("wyh"));
        assertTrue(csv.contains("13800138000"));
        assertTrue(csv.contains("有效"));
        assertTrue(csv.contains("王富贵"));
    }

    @Test
    void importByTags_createsUpdatedAndSkipped() {
        stubOperator(99L, "导入员");

        ViewerTagEntity tag = new ViewerTagEntity();
        tag.setId(7L);
        tag.setName("高意向");
        when(viewerTagRepository.findByIdAndDeletedTimeIsNull(7L)).thenReturn(Optional.of(tag));

        ViewerTagMemberEntity m1 = new ViewerTagMemberEntity();
        m1.setUsername("10");
        m1.setTagId(7L);
        ViewerTagMemberEntity m2 = new ViewerTagMemberEntity();
        m2.setUsername("11");
        m2.setTagId(7L);
        ViewerTagMemberEntity m3 = new ViewerTagMemberEntity();
        m3.setUsername("12");
        m3.setTagId(7L);
        // duplicate viewer under same tag set → still one
        ViewerTagMemberEntity m1Dup = new ViewerTagMemberEntity();
        m1Dup.setUsername("10");
        m1Dup.setTagId(7L);
        when(viewerTagMemberRepository.findByTagIdIn(List.of(7L))).thenReturn(List.of(m1, m2, m3, m1Dup));

        when(audienceRepository.findByActivityIdAndUsername(ACTIVITY_ID, "10")).thenReturn(Optional.empty());
        when(audienceRepository.findByActivityIdAndUsername(ACTIVITY_ID, "11"))
                .thenReturn(Optional.of(audienceRow(201L, 11L, null)));
        when(audienceRepository.findByActivityIdAndUsername(ACTIVITY_ID, "12"))
                .thenReturn(Optional.of(audienceRow(202L, 12L, 88L)));
        when(audienceRepository.save(any(ActivityAudienceEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ImportAudienceByTagsRequest req = new ImportAudienceByTagsRequest();
        req.setTagIds(List.of(7L));
        var resp = service.importByTags(ACTIVITY_ID, req, 99L);
        assertEquals(3, resp.getTotal());
        assertEquals(1, resp.getCreated());
        assertEquals(1, resp.getUpdated());
        assertEquals(1, resp.getSkipped());
    }

    @Test
    void estimateByTags_returnsDistinctViewerCount() {
        ViewerTagEntity tag1 = new ViewerTagEntity();
        tag1.setId(7L);
        tag1.setName("A");
        ViewerTagEntity tag2 = new ViewerTagEntity();
        tag2.setId(8L);
        tag2.setName("B");
        when(viewerTagRepository.findByIdAndDeletedTimeIsNull(7L)).thenReturn(Optional.of(tag1));
        when(viewerTagRepository.findByIdAndDeletedTimeIsNull(8L)).thenReturn(Optional.of(tag2));
        when(viewerTagMemberRepository.countDistinctViewersByTagIdIn(List.of(7L, 8L))).thenReturn(2L);

        ImportAudienceByTagsRequest req = new ImportAudienceByTagsRequest();
        req.setTagIds(List.of(7L, 8L));
        var resp = service.estimateByTags(ACTIVITY_ID, req);
        assertEquals(2, resp.getTotal());
    }

    @Test
    void importByTags_emptyTagIds_rejects() {
        ImportAudienceByTagsRequest req = new ImportAudienceByTagsRequest();
        req.setTagIds(List.of());
        assertThrows(IllegalArgumentException.class, () -> service.importByTags(ACTIVITY_ID, req, 99L));
    }

    @Test
    void importByTags_moreThanFive_rejects() {
        ImportAudienceByTagsRequest req = new ImportAudienceByTagsRequest();
        req.setTagIds(List.of(1L, 2L, 3L, 4L, 5L, 6L));
        assertThrows(IllegalArgumentException.class, () -> service.importByTags(ACTIVITY_ID, req, 99L));
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
        v.setUsername(Long.toString(id));
        v.setPhone(phone);
        return v;
    }

    private static ActivityAudienceEntity audienceRow(long id, long viewerId, Long importerId) {
        ActivityAudienceEntity row = new ActivityAudienceEntity();
        row.setId(id);
        row.setActivityId(ACTIVITY_ID);
        row.setUsername(Long.toString(viewerId));
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
