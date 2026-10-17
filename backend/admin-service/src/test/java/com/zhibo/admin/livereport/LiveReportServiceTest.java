package com.zhibo.admin.livereport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.livereport.LiveReportAuditRepository;
import com.zhibo.livereport.LiveReportEntity;
import com.zhibo.livereport.LiveReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LiveReportServiceTest {
    private final LiveReportRepository reports = mock(LiveReportRepository.class);
    private final LiveReportAuditRepository audits = mock(LiveReportAuditRepository.class);
    private final SysUserAccountRepository users = mock(SysUserAccountRepository.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private LiveReportService service;

    @BeforeEach
    void setUp() {
        service = new LiveReportService(reports, audits, users, mapper);
        SysUserAccountEntity operator = new SysUserAccountEntity();
        operator.setId(7L);
        operator.setRsStatus(1);
        operator.setUserName("审核员");
        when(users.findById(7L)).thenReturn(Optional.of(operator));
    }

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
        DateTimeFormatter format = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return new LiveReportDtos.PlanTime(LocalDateTime.now().plusDays(startDays).format(format),
                LocalDateTime.now().plusDays(endDays).format(format));
    }
}
