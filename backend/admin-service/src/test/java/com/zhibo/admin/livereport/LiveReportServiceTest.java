package com.zhibo.admin.livereport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.livereport.LiveReportAuditEntity;
import com.zhibo.livereport.LiveReportAuditRepository;
import com.zhibo.livereport.LiveReportEntity;
import com.zhibo.livereport.LiveReportPlanEntity;
import com.zhibo.livereport.LiveReportPlanRepository;
import com.zhibo.livereport.LiveReportRepository;
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveReportServiceTest {
    private static final String REPORT_ID = "3f6d06e29f8d4f5f9f3a2c9f7f0a1b01";
    private static final String REPORT_ID_2 = "4a7e17f30a9e4b5c8d6e1f2a3b4c5d6e";
    private static final String COVER_MATERIAL_ID = "aaaaaaaabbbb4ccc8dddeeeeeeeeeeee";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 固定时钟：2026-10-17 14:00:00（未来第 7 天即 2026-10-24，当日 23:59:59 可用） */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 14, 0, 0);

    private final LiveReportRepository reports = mock(LiveReportRepository.class);
    private final LiveReportPlanRepository planRows = mock(LiveReportPlanRepository.class);
    private final LiveReportAuditRepository audits = mock(LiveReportAuditRepository.class);
    private final SysUserAccountRepository users = mock(SysUserAccountRepository.class);
    private final MaterialRepository materials = mock(MaterialRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private LiveReportService service;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new LiveReportService(reports, planRows, audits, users, materials, mapper, fixed);
        when(users.findById(7L)).thenReturn(Optional.of(user(7L, "审核员")));
        when(users.findById(8L)).thenReturn(Optional.of(user(8L, "张美丽")));
        when(materials.findById(COVER_MATERIAL_ID)).thenReturn(Optional.of(passedImage(1024L)));
        when(planRows.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
    }

    // ---------- 创建后不可编辑 ----------

    @Test
    void updateRejectedRegardlessOfStatus() {
        for (String status : List.of("pending", "passed", "rejected")) {
            LiveReportEntity entity = report(status, "passed".equals(status) ? 2 : 0);
            when(reports.findById(REPORT_ID)).thenReturn(Optional.of(entity));
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> service.update(REPORT_ID, new LiveReportDtos.UpdateRequest(
                            "标题", "内容", List.of(plan(1, 2)), null, null, null, null, null, null, null), 7L));
            assertEquals("报备不可编辑", error.getMessage());
        }
        verify(reports, never()).save(any());
    }

    @Test
    void updateMissingReport() {
        when(reports.findById(REPORT_ID)).thenReturn(Optional.empty());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.update(REPORT_ID, new LiveReportDtos.UpdateRequest(null, null, null,
                        null, null, null, null, null, null, null), 7L));
        assertEquals("报备不存在", error.getMessage());
    }

    // ---------- 新建校验 ----------

    @Test
    void createAcceptsSeventhDayEndOfDay() {
        LiveReportDtos.SaveRequest req = validRequestWithTimes(
                List.of(plan("2026-10-18 10:00:00", "2026-10-24 23:59:59")));

        LiveReportDtos.CreateResult created = service.create(req, 7L);
        assertEquals(1, created.list().size());
        LiveReportDtos.Result result = created.list().get(0);

        assertNotNull(result.id());
        assertEquals(32, result.id().length());
        assertTrue(result.id().matches("[0-9a-f]{32}"));
        assertEquals("pending", result.status());
        verify(reports).save(argThat((LiveReportEntity e) ->
                "pending".equals(e.getStatus()) && Long.valueOf(8L).equals(e.getHostId())));
        verify(planRows).deleteByReportId(eq(result.id()));
        verify(planRows).saveAll(argThat((List<LiveReportPlanEntity> rows) ->
                rows.size() == 1
                        && rows.get(0).getReportId().equals(result.id())
                        && rows.get(0).getSortOrder() == 0
                        && LocalDateTime.of(2026, 10, 18, 10, 0).equals(rows.get(0).getStartTime())
                        && LocalDateTime.of(2026, 10, 24, 23, 59, 59).equals(rows.get(0).getEndTime())));
        verify(audits).save(argThat((LiveReportAuditEntity a) -> "submit".equals(a.getAction())));
    }

    @Test
    @SuppressWarnings("unchecked")
    void createSplitsEachPlanTimeIntoItsOwnReport() {
        LiveReportDtos.SaveRequest req = validRequestWithTimes(List.of(
                plan("2026-10-18 10:00:00", "2026-10-19 12:00:00"),
                plan("2026-10-22 10:00:00", "2026-10-23 12:00:00")));

        LiveReportDtos.CreateResult created = service.create(req, 7L);

        assertEquals(2, created.list().size());
        assertNotEquals(created.list().get(0).id(), created.list().get(1).id());
        assertEquals("pending", created.list().get(0).status());
        assertEquals("pending", created.list().get(1).status());

        ArgumentCaptor<LiveReportEntity> saved = ArgumentCaptor.forClass(LiveReportEntity.class);
        verify(reports, times(2)).save(saved.capture());
        List<LiveReportEntity> rows = saved.getAllValues();
        assertEquals(created.list().get(0).id(), rows.get(0).getId());
        assertEquals(created.list().get(1).id(), rows.get(1).getId());
        assertEquals("标题", rows.get(0).getTitle());
        assertEquals(rows.get(0).getTitle(), rows.get(1).getTitle());
        assertEquals(rows.get(0).getContent(), rows.get(1).getContent());
        assertEquals(rows.get(0).getCoverObjectKey(), rows.get(1).getCoverObjectKey());
        assertEquals(rows.get(0).getHostId(), rows.get(1).getHostId());
        assertEquals(rows.get(0).getAttachments(), rows.get(1).getAttachments());
        assertEquals(NOW, rows.get(0).getSubmittedTime());
        assertEquals(rows.get(0).getSubmittedTime(), rows.get(1).getSubmittedTime());
        assertTrue(rows.get(0).getPlanTimes().contains("2026-10-18 10:00:00"));
        assertFalse(rows.get(0).getPlanTimes().contains("2026-10-22"));
        assertTrue(rows.get(1).getPlanTimes().contains("2026-10-22 10:00:00"));
        assertFalse(rows.get(1).getPlanTimes().contains("2026-10-18 10:00:00"));

        ArgumentCaptor<List<LiveReportPlanEntity>> planCap = ArgumentCaptor.forClass(List.class);
        verify(planRows, times(2)).saveAll(planCap.capture());
        assertEquals(1, planCap.getAllValues().get(0).size());
        assertEquals(rows.get(0).getId(), planCap.getAllValues().get(0).get(0).getReportId());
        assertEquals(0, planCap.getAllValues().get(0).get(0).getSortOrder());
        assertEquals(LocalDateTime.of(2026, 10, 18, 10, 0), planCap.getAllValues().get(0).get(0).getStartTime());
        assertEquals(1, planCap.getAllValues().get(1).size());
        assertEquals(rows.get(1).getId(), planCap.getAllValues().get(1).get(0).getReportId());
        assertEquals(LocalDateTime.of(2026, 10, 22, 10, 0), planCap.getAllValues().get(1).get(0).getStartTime());

        ArgumentCaptor<LiveReportAuditEntity> auditCap = ArgumentCaptor.forClass(LiveReportAuditEntity.class);
        verify(audits, times(2)).save(auditCap.capture());
        assertEquals("submit", auditCap.getAllValues().get(0).getAction());
        assertEquals(rows.get(0).getId(), auditCap.getAllValues().get(0).getReportId());
        assertEquals("submit", auditCap.getAllValues().get(1).getAction());
        assertEquals(rows.get(1).getId(), auditCap.getAllValues().get(1).getReportId());
    }

    @Test
    void createRejectsEighthDay() {
        LiveReportDtos.SaveRequest req = validRequestWithTimes(
                List.of(plan("2026-10-18 10:00:00", "2026-10-25 00:00:00")));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(req, 7L));

        assertEquals("报备计划截止日期不能超过未来第 7 天", error.getMessage());
        verify(reports, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createRejectsMoreThanTenPlans() {
        List<LiveReportDtos.PlanTime> times = new ArrayList<>();
        for (int i = 0; i < 11; i++) times.add(plan("2026-10-18 10:00:00", "2026-10-18 11:00:00"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(validRequestWithTimes(times), 7L));

        assertEquals("计划时间需为 1~10 段", error.getMessage());
    }

    @Test
    void createAssignsServerUuid() {
        LiveReportDtos.CreateResult created = service.create(validRequest(), 7L);
        LiveReportDtos.Result result = created.list().get(0);

        assertEquals(32, result.id().length());
        assertTrue(result.id().matches("[0-9a-f]{32}"));
        verify(reports).save(argThat((LiveReportEntity e) -> result.id().equals(e.getId())));
    }

    @Test
    void createRejectsAttachmentKeyedByClientReportId() {
        LiveReportDtos.Attachment bad = new LiveReportDtos.Attachment(
                "a.pdf", "https://cdn/a.pdf", "live-report/" + REPORT_ID + "/attachment/a.pdf", 1024L, "application/pdf");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(validRequestWithAttachments(List.of(bad)), 7L));

        assertEquals("材料仅支持 doc、docx、pdf、txt，且大小不超过 10MB", error.getMessage());
    }

    @Test
    void createRejectsBadAttachmentType() {
        LiveReportDtos.Attachment bad = attachment("a.exe", 1024L);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(validRequestWithAttachments(List.of(bad)), 7L));

        assertEquals("材料仅支持 doc、docx、pdf、txt，且大小不超过 10MB", error.getMessage());
    }

    @Test
    void createRejectsOversizedAttachment() {
        LiveReportDtos.Attachment bad = attachment("a.pdf", 11L * 1024 * 1024);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(validRequestWithAttachments(List.of(bad)), 7L));

        assertEquals("材料仅支持 doc、docx、pdf、txt，且大小不超过 10MB", error.getMessage());
    }

    @Test
    void createRejectsAttachmentKeyOutsidePrefix() {
        LiveReportDtos.Attachment bad = new LiveReportDtos.Attachment(
                "a.pdf", "https://cdn/a.pdf", "material/document/a.pdf", 1024L, "application/pdf");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(validRequestWithAttachments(List.of(bad)), 7L));

        assertEquals("材料仅支持 doc、docx、pdf、txt，且大小不超过 10MB", error.getMessage());
    }

    @Test
    void createRejectsMoreThanOneAttachment() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(validRequestWithAttachments(List.of(
                        attachment("a.pdf", 1024L), attachment("b.pdf", 1024L))), 7L));

        assertEquals("材料只能上传 1 份", error.getMessage());
        verify(reports, never()).save(any());
    }

    @Test
    void createAcceptsTxtAttachment() {
        LiveReportDtos.CreateResult created = service.create(
                validRequestWithAttachments(List.of(attachment("说明.txt", 1024L))), 7L);

        assertEquals(1, created.list().size());
        assertEquals("pending", created.list().get(0).status());
        verify(reports).save(argThat((LiveReportEntity e) ->
                e.getAttachments() != null && e.getAttachments().contains("说明.txt")));
    }

    @Test
    void createRejectsCoverOverFiveMB() {
        when(materials.findById(COVER_MATERIAL_ID)).thenReturn(Optional.of(passedImage(6L * 1024 * 1024)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(validRequest(), 7L));

        assertEquals("封面大小须不超过 5MB", error.getMessage());
    }

    @Test
    void createRejectsUnapprovedCover() {
        MaterialEntity pending = passedImage(1024L);
        pending.setAiAuditStatus(0);
        when(materials.findById(COVER_MATERIAL_ID)).thenReturn(Optional.of(pending));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(validRequest(), 7L));

        assertEquals("封面须来自素材中心已审核通过的图片", error.getMessage());
    }

    @Test
    void createRejectsBlankCoverUrl() {
        LiveReportDtos.SaveRequest base = validRequest();
        LiveReportDtos.SaveRequest req = new LiveReportDtos.SaveRequest(base.title(),
                base.content(), base.planTimes(), "  ", base.coverObjectKey(),
                base.coverFileSize(), base.coverMimeType(), base.coverMaterialId(), base.hostId(),
                base.attachments());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(req, 7L));

        assertEquals("请选择报备封面", error.getMessage());
    }

    // ---------- 审核 ----------

    @Test
    void auditAllowsReAuditAfterPassedOrRejected() {
        LiveReportEntity passed = report("passed", 0);
        when(reports.findById(REPORT_ID)).thenReturn(Optional.of(passed));

        LiveReportDtos.Result reReject = service.audit(REPORT_ID,
                new LiveReportDtos.AuditRequest("rejected", "资料不全"), 7L);

        assertEquals("rejected", reReject.status());
        assertEquals("rejected", passed.getStatus());
        verify(audits).save(argThat((LiveReportAuditEntity a) ->
                "audit".equals(a.getAction()) && "rejected".equals(a.getResult())
                        && "资料不全".equals(a.getReason())));

        LiveReportEntity rejected = report("rejected", 0);
        when(reports.findById(REPORT_ID_2)).thenReturn(Optional.of(rejected));

        LiveReportDtos.Result rePass = service.audit(REPORT_ID_2,
                new LiveReportDtos.AuditRequest("passed", null), 7L);

        assertEquals("passed", rePass.status());
        assertEquals("passed", rejected.getStatus());
    }

    @Test
    void auditRejectRequiresReason() {
        LiveReportEntity report = report("pending", 0);
        when(reports.findById(REPORT_ID)).thenReturn(Optional.of(report));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.audit(REPORT_ID, new LiveReportDtos.AuditRequest("rejected", "   "), 7L));

        assertEquals("驳回原因不能为空", error.getMessage());
        verify(reports, never()).save(report);
    }

    @Test
    void auditPassedWritesRecord() {
        LiveReportEntity report = report("pending", 0);
        when(reports.findById(REPORT_ID)).thenReturn(Optional.of(report));

        LiveReportDtos.Result result = service.audit(REPORT_ID,
                new LiveReportDtos.AuditRequest("passed", null), 7L);

        assertEquals("passed", result.status());
        assertEquals("passed", report.getStatus());
        verify(audits).save(argThat((LiveReportAuditEntity a) ->
                "audit".equals(a.getAction()) && "passed".equals(a.getResult())
                        && Long.valueOf(7L).equals(a.getOperatorId())));
    }

    @Test
    void auditPassedWithReasonPersistsReason() {
        LiveReportEntity report = report("pending", 0);
        when(reports.findById(REPORT_ID)).thenReturn(Optional.of(report));

        service.audit(REPORT_ID, new LiveReportDtos.AuditRequest("passed", "  内容合规  "), 7L);

        verify(audits).save(argThat((LiveReportAuditEntity a) ->
                "audit".equals(a.getAction())
                        && "passed".equals(a.getResult())
                        && "内容合规".equals(a.getReason())));
    }

    @Test
    void softDelete_setsActorAndTime() {
        LiveReportEntity report = report("passed", 2);
        when(reports.findById(REPORT_ID)).thenReturn(Optional.of(report));

        LiveReportDtos.DeleteResult result = service.softDelete(REPORT_ID, 7L);

        assertEquals(REPORT_ID, result.id());
        assertNotNull(report.getDeletedTime());
        assertEquals(7L, report.getDeletedBy());
        assertEquals("审核员", report.getDeletedByName());
        assertEquals(2, report.getBoundLiveCount());
        verify(reports).save(report);
    }

    @Test
    void softDelete_alreadyDeleted_rejects() {
        LiveReportEntity report = report("pending", 0);
        report.setDeletedTime(NOW);
        when(reports.findById(REPORT_ID)).thenReturn(Optional.of(report));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.softDelete(REPORT_ID, 7L));
        assertEquals("报备不存在", error.getMessage());
        verify(reports, never()).save(report);
    }

    @Test
    void detail_softDeleted_rejects() {
        LiveReportEntity report = report("pending", 0);
        report.setDeletedTime(NOW);
        when(reports.findById(REPORT_ID)).thenReturn(Optional.of(report));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.detail(REPORT_ID));
        assertEquals("报备不存在", error.getMessage());
    }

    // ---------- helper ----------

    private SysUserAccountEntity user(long id, String name) {
        SysUserAccountEntity u = new SysUserAccountEntity();
        u.setId(id);
        u.setRsStatus(1);
        u.setUserName(name);
        return u;
    }

    private LiveReportEntity report(String status, int boundCount) {
        LiveReportEntity report = new LiveReportEntity();
        report.setId(REPORT_ID);
        report.setStatus(status);
        report.setBoundLiveCount(boundCount);
        report.setPlanTimes("[]");
        return report;
    }

    private LiveReportDtos.PlanTime plan(int startDays, int endDays) {
        return new LiveReportDtos.PlanTime(NOW.plusDays(startDays).format(FMT),
                NOW.plusDays(endDays).format(FMT));
    }

    private MaterialEntity passedImage(long fileSize) {
        MaterialEntity m = new MaterialEntity();
        m.setId(COVER_MATERIAL_ID);
        m.setType("image");
        m.setName("x.jpg");
        m.setUrl("https://cdn/x.jpg");
        m.setObjectKey("material/image/x.jpg");
        m.setFileSize(fileSize);
        m.setMimeType("image/jpeg");
        m.setAiAuditStatus(1);
        m.setRiskAuditStatus(1);
        return m;
    }

    private LiveReportDtos.PlanTime plan(String startTime, String endTime) {
        return new LiveReportDtos.PlanTime(startTime, endTime);
    }

    private LiveReportDtos.Attachment attachment(String name, long fileSize) {
        return new LiveReportDtos.Attachment(name, "https://cdn/" + name,
                "live-report/attachment/" + name, fileSize, "application/pdf");
    }

    private LiveReportDtos.SaveRequest validRequest() {
        return validRequestWithTimes(List.of(plan("2026-10-18 10:00:00", "2026-10-18 12:00:00")));
    }

    private LiveReportDtos.SaveRequest validRequestWithTimes(List<LiveReportDtos.PlanTime> times) {
        return new LiveReportDtos.SaveRequest("标题", "内容", times,
                "https://cdn/x.jpg", "material/image/x.jpg", 1024L, "image/jpeg", COVER_MATERIAL_ID, 8L,
                List.of(attachment("a.pdf", 1024L)));
    }

    private LiveReportDtos.SaveRequest validRequestWithAttachments(List<LiveReportDtos.Attachment> files) {
        return new LiveReportDtos.SaveRequest("标题", "内容",
                List.of(plan("2026-10-18 10:00:00", "2026-10-18 12:00:00")),
                "https://cdn/x.jpg", "material/image/x.jpg", 1024L, "image/jpeg", COVER_MATERIAL_ID, 8L, files);
    }
}
