package com.zhibo.admin.livereport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.livereport.LiveReportAuditEntity;
import com.zhibo.livereport.LiveReportAuditRepository;
import com.zhibo.livereport.LiveReportEntity;
import com.zhibo.livereport.LiveReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveReportServiceTest {
    private static final String REPORT_ID = "3f6d06e2-9f8d-4f5f-9f3a-2c9f7f0a1b01";
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    /** 固定时钟：2026-10-17 14:00:00（未来第 7 天即 2026-10-24，当日 23:59:59 可用） */
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 17, 14, 0, 0);

    private final LiveReportRepository reports = mock(LiveReportRepository.class);
    private final LiveReportAuditRepository audits = mock(LiveReportAuditRepository.class);
    private final SysUserAccountRepository users = mock(SysUserAccountRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private LiveReportService service;

    @BeforeEach
    void setUp() {
        Clock fixed = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);
        service = new LiveReportService(reports, audits, users, mapper, fixed);
        when(users.findById(7L)).thenReturn(Optional.of(user(7L, "审核员")));
        when(users.findById(8L)).thenReturn(Optional.of(user(8L, "张美丽")));
    }

    // ---------- 状态编辑门禁 ----------

    @Test
    void pendingReportCannotBeEdited() {
        LiveReportEntity report = report("pending", 0);
        when(reports.findById("r1")).thenReturn(Optional.of(report));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.update("r1", new LiveReportDtos.UpdateRequest(null, null, null,
                        null, null, null, null, null, null), 7L));

        assertEquals("待审核报备不可编辑", error.getMessage());
        verify(reports, never()).save(report);
    }

    @Test
    void boundPassedReportCannotBeEdited() {
        LiveReportEntity report = report("passed", 1);
        when(reports.findById("r1")).thenReturn(Optional.of(report));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.update("r1", new LiveReportDtos.UpdateRequest(null, null, List.of(plan(2, 3)),
                        null, null, null, null, null, null), 7L));

        assertEquals("已绑定直播的报备不可编辑", error.getMessage());
    }

    @Test
    void passedReportOnlyAppendsPlanTimes() throws Exception {
        LiveReportEntity report = report("passed", 0);
        LiveReportDtos.PlanTime first = plan(1, 2);
        report.setPlanTimes(mapper.writeValueAsString(List.of(first)));
        when(reports.findById("r1")).thenReturn(Optional.of(report));

        LiveReportDtos.Result result = service.update("r1", new LiveReportDtos.UpdateRequest(
                null, null, List.of(first, plan(3, 4)), null, null, null, null, null, null), 7L);

        assertEquals("passed", result.status());
        verify(reports).save(report);
        // 追加计划时间须留审计记录
        verify(audits).save(argThat((LiveReportAuditEntity a) ->
                "append".equals(a.getAction()) && a.getReason() != null && a.getReason().contains("1 段")));
    }

    @Test
    void passedAppendRejectsModifiedPrefix() throws Exception {
        LiveReportEntity report = report("passed", 0);
        LiveReportDtos.PlanTime first = plan(1, 2);
        report.setPlanTimes(mapper.writeValueAsString(List.of(first)));
        when(reports.findById("r1")).thenReturn(Optional.of(report));

        // 旧段被改动（结束时间不同），即使整体更长也必须拒绝
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.update("r1", new LiveReportDtos.UpdateRequest(null, null,
                        List.of(plan(1, 3), plan(3, 4)), null, null, null, null, null, null), 7L));

        assertEquals("已通过报备只能在末尾增加计划时间", error.getMessage());
        verify(reports, never()).save(report);
    }

    // ---------- 新建校验 ----------

    @Test
    void createAcceptsSeventhDayEndOfDay() {
        LiveReportDtos.SaveRequest req = validRequestWithTimes(
                List.of(plan("2026-10-18 10:00:00", "2026-10-24 23:59:59")));

        LiveReportDtos.Result result = service.create(req, 7L);

        assertEquals(REPORT_ID, result.id());
        assertEquals("pending", result.status());
        verify(reports).save(argThat((LiveReportEntity e) ->
                "pending".equals(e.getStatus()) && Long.valueOf(8L).equals(e.getHostId())));
        verify(audits).save(argThat((LiveReportAuditEntity a) -> "submit".equals(a.getAction())));
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
    void createRejectsDuplicateId() {
        when(reports.existsById(REPORT_ID)).thenReturn(true);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(validRequest(), 7L));

        assertEquals("报备 ID 已存在", error.getMessage());
        verify(reports, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void createRejectsBadAttachmentType() {
        LiveReportDtos.Attachment bad = attachment("a.exe", 1024L);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(validRequestWithAttachments(List.of(bad)), 7L));

        assertEquals("材料仅支持 doc/docx/pdf，单份不超过 10MB", error.getMessage());
    }

    @Test
    void createRejectsOversizedAttachment() {
        LiveReportDtos.Attachment bad = attachment("a.pdf", 11L * 1024 * 1024);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(validRequestWithAttachments(List.of(bad)), 7L));

        assertEquals("材料仅支持 doc/docx/pdf，单份不超过 10MB", error.getMessage());
    }

    @Test
    void createRejectsAttachmentKeyOutsidePrefix() {
        LiveReportDtos.Attachment bad = new LiveReportDtos.Attachment(
                "a.pdf", "https://cdn/a.pdf", "material/document/a.pdf", 1024L, "application/pdf");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(validRequestWithAttachments(List.of(bad)), 7L));

        assertEquals("材料仅支持 doc/docx/pdf，单份不超过 10MB", error.getMessage());
    }

    @Test
    void createRejectsCoverOverFiveMB() {
        LiveReportDtos.SaveRequest req = new LiveReportDtos.SaveRequest(REPORT_ID, "标题", "内容",
                List.of(plan("2026-10-18 10:00:00", "2026-10-18 12:00:00")),
                "https://cdn/x.jpg", "material/image/x.jpg", 6L * 1024 * 1024, "image/jpeg", "8",
                List.of(attachment("a.pdf", 1024L)));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(req, 7L));

        assertEquals("封面大小须不超过 5MB", error.getMessage());
    }

    @Test
    void createRejectsInvalidUuid() {
        LiveReportDtos.SaveRequest base = validRequest();
        LiveReportDtos.SaveRequest req = new LiveReportDtos.SaveRequest("not-a-uuid", base.title(),
                base.content(), base.planTimes(), base.coverUrl(), base.coverObjectKey(),
                base.coverFileSize(), base.coverMimeType(), base.hostId(), base.attachments());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(req, 7L));

        assertEquals("报备 ID 格式错误", error.getMessage());
    }

    // ---------- 驳回后重新提交 ----------

    @Test
    void rejectedResubmitRefreshesSubmitterAndStatus() {
        LiveReportEntity report = report("rejected", 0);
        report.setSubmitterId(99L);
        report.setSubmitterName("旧提交人");
        when(reports.findById("r1")).thenReturn(Optional.of(report));

        // 附件对象键须以实际报备 ID 组织（编辑不改变报备 ID）
        LiveReportDtos.Attachment file = new LiveReportDtos.Attachment("a.pdf", "https://cdn/a.pdf",
                "live-report/r1/attachment/a.pdf", 1024L, "application/pdf");
        LiveReportDtos.Result result = service.update("r1", new LiveReportDtos.UpdateRequest(
                "标题", "内容", List.of(plan("2026-10-18 10:00:00", "2026-10-18 12:00:00")),
                "https://cdn/x.jpg", "material/image/x.jpg", 1024L, "image/jpeg", "8",
                List.of(file)), 7L);

        assertEquals("pending", result.status());
        assertEquals("pending", report.getStatus());
        // 重新提交须刷新提交人快照为当前操作人
        assertEquals(7L, report.getSubmitterId());
        assertEquals("审核员", report.getSubmitterName());
        assertEquals(NOW, report.getSubmittedAt());
        verify(reports).save(report);
        verify(audits).save(argThat((LiveReportAuditEntity a) -> "resubmit".equals(a.getAction())));
    }

    // ---------- 审核 ----------

    @Test
    void auditRejectsNonPendingReport() {
        LiveReportEntity report = report("passed", 0);
        when(reports.findById("r1")).thenReturn(Optional.of(report));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.audit("r1", new LiveReportDtos.AuditRequest("passed", null), 7L));

        assertEquals("仅待审核报备可审核", error.getMessage());
    }

    @Test
    void auditRejectRequiresReason() {
        LiveReportEntity report = report("pending", 0);
        when(reports.findById("r1")).thenReturn(Optional.of(report));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.audit("r1", new LiveReportDtos.AuditRequest("rejected", "   "), 7L));

        assertEquals("驳回原因不能为空", error.getMessage());
        verify(reports, never()).save(report);
    }

    @Test
    void auditPassedWritesRecord() {
        LiveReportEntity report = report("pending", 0);
        when(reports.findById("r1")).thenReturn(Optional.of(report));

        LiveReportDtos.Result result = service.audit("r1",
                new LiveReportDtos.AuditRequest("passed", null), 7L);

        assertEquals("passed", result.status());
        assertEquals("passed", report.getStatus());
        verify(audits).save(argThat((LiveReportAuditEntity a) ->
                "audit".equals(a.getAction()) && "passed".equals(a.getResult())
                        && Long.valueOf(7L).equals(a.getOperatorId())));
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
        report.setId("r1");
        report.setStatus(status);
        report.setBoundLiveCount(boundCount);
        report.setPlanTimes("[]");
        return report;
    }

    private LiveReportDtos.PlanTime plan(int startDays, int endDays) {
        return new LiveReportDtos.PlanTime(LocalDateTime.now().plusDays(startDays).format(FMT),
                LocalDateTime.now().plusDays(endDays).format(FMT));
    }

    private LiveReportDtos.PlanTime plan(String startTime, String endTime) {
        return new LiveReportDtos.PlanTime(startTime, endTime);
    }

    private LiveReportDtos.Attachment attachment(String name, long fileSize) {
        return new LiveReportDtos.Attachment(name, "https://cdn/" + name,
                "live-report/" + REPORT_ID + "/attachment/" + name, fileSize, "application/pdf");
    }

    private LiveReportDtos.SaveRequest validRequest() {
        return validRequestWithTimes(List.of(plan("2026-10-18 10:00:00", "2026-10-18 12:00:00")));
    }

    private LiveReportDtos.SaveRequest validRequestWithTimes(List<LiveReportDtos.PlanTime> times) {
        return new LiveReportDtos.SaveRequest(REPORT_ID, "标题", "内容", times,
                "https://cdn/x.jpg", "material/image/x.jpg", 1024L, "image/jpeg", "8",
                List.of(attachment("a.pdf", 1024L)));
    }

    private LiveReportDtos.SaveRequest validRequestWithAttachments(List<LiveReportDtos.Attachment> files) {
        return new LiveReportDtos.SaveRequest(REPORT_ID, "标题", "内容",
                List.of(plan("2026-10-18 10:00:00", "2026-10-18 12:00:00")),
                "https://cdn/x.jpg", "material/image/x.jpg", 1024L, "image/jpeg", "8", files);
    }
}
