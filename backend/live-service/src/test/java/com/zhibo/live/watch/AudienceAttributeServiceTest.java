package com.zhibo.live.watch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityAudienceEntity;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.live.org.EmployeeAccountEntity;
import com.zhibo.live.org.EmployeeAccountRepository;
import com.zhibo.live.org.HrEmployeeEntity;
import com.zhibo.live.org.HrEmployeePostEntity;
import com.zhibo.live.org.HrEmployeePostRepository;
import com.zhibo.live.org.HrEmployeeRepository;
import com.zhibo.live.watch.dto.AttributeAudienceRequest;
import com.zhibo.viewer.ViewerRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AudienceAttributeServiceTest {

    private static final String AID = "a1b2c3d4e5f67890abcdef1234567890";
    private static final String HR_IDENTIFIER = "a0d9a8a1-a6d8-4978-8cb4-e7e86fba9c39";

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivityAudienceRepository audienceRepository;
    @Mock
    private ViewerRepository viewerRepository;
    @Mock
    private EmployeeAccountRepository employeeAccountRepository;
    @Mock
    private HrEmployeeRepository hrEmployeeRepository;
    @Mock
    private HrEmployeePostRepository hrEmployeePostRepository;

    private AudienceAttributeService service;

    @BeforeEach
    void setUp() {
        service = new AudienceAttributeService(
                activityRepository,
                audienceRepository,
                viewerRepository,
                employeeAccountRepository,
                hrEmployeeRepository,
                hrEmployeePostRepository);
    }

    @Test
    void normalizeKind_defaultsToEmployee() {
        assertEquals("employee", AudienceAttributeService.normalizeKind(null));
        assertEquals("employee", AudienceAttributeService.normalizeKind("  "));
        assertEquals("audience", AudienceAttributeService.normalizeKind("Audience"));
    }

    @Test
    void attribute_employee_writesInviterWhenEmpty() {
        stubActivityAndViewer();
        when(audienceRepository.findByActivityIdAndUsername(AID, "12")).thenReturn(Optional.empty());
        when(audienceRepository.save(any())).thenAnswer(inv -> {
            ActivityAudienceEntity row = inv.getArgument(0);
            row.setId(99L);
            return row;
        });

        AttributeAudienceRequest req = new AttributeAudienceRequest();
        req.setInviterId(10001L);
        req.setInviterKind("employee");

        var res = service.attribute(AID, "12", req);
        assertEquals(99L, res.getId());
        assertEquals("12", res.getUsername());

        ArgumentCaptor<ActivityAudienceEntity> cap =
                ArgumentCaptor.forClass(ActivityAudienceEntity.class);
        verify(audienceRepository).save(cap.capture());
        assertEquals(10001L, cap.getValue().getEmployeeInviterId());
    }

    @Test
    void attribute_audience_rejectedWithoutSave() {
        stubActivityAndViewer();

        AttributeAudienceRequest req = new AttributeAudienceRequest();
        req.setInviterId(34L);
        req.setInviterKind("audience");

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class, () -> service.attribute(AID, "12", req));
        assertEquals("不记录观众邀请", ex.getMessage());
        verify(audienceRepository, never()).save(any());
    }

    @Test
    void attribute_unknownKind_rejected() {
        stubActivityAndViewer();

        AttributeAudienceRequest req = new AttributeAudienceRequest();
        req.setInviterId(10001L);
        req.setInviterKind("bot");

        IllegalArgumentException ex =
                assertThrows(
                        IllegalArgumentException.class, () -> service.attribute(AID, "12", req));
        assertEquals("inviterKind 须为 employee 或 audience", ex.getMessage());
    }

    @Test
    void resolveSalesman_usesInviterIdentifierAndDept() {
        stubActivityOnly();
        ActivityAudienceEntity row = new ActivityAudienceEntity();
        row.setEmployeeInviterId(277L);
        row.setImporterId(20002L);
        when(audienceRepository.findByActivityIdAndUsername(AID, "12")).thenReturn(Optional.of(row));
        stubInviter(277L, 12476L, HR_IDENTIFIER, 94L);

        var res = service.resolveSalesman(AID, "12");
        assertEquals(HR_IDENTIFIER, res.getSalesmanId());
        assertEquals(94L, res.getSalesmanDeptId());
    }

    @Test
    void resolveSalesman_ignoresImporterOnly() {
        stubActivityOnly();
        ActivityAudienceEntity row = new ActivityAudienceEntity();
        row.setImporterId(20002L);
        when(audienceRepository.findByActivityIdAndUsername(AID, "12")).thenReturn(Optional.of(row));

        var res = service.resolveSalesman(AID, "12");
        assertNull(res.getSalesmanId());
        assertNull(res.getSalesmanDeptId());
        verify(employeeAccountRepository, never()).findById(any());
    }

    @Test
    void resolveSalesman_nullWhenNoRow() {
        stubActivityOnly();
        when(audienceRepository.findByActivityIdAndUsername(AID, "12")).thenReturn(Optional.empty());

        var res = service.resolveSalesman(AID, "12");
        assertNull(res.getSalesmanId());
        assertNull(res.getSalesmanDeptId());
    }

    @Test
    void resolveSalesman_identifierWithoutDept() {
        stubActivityOnly();
        ActivityAudienceEntity row = new ActivityAudienceEntity();
        row.setEmployeeInviterId(277L);
        when(audienceRepository.findByActivityIdAndUsername(AID, "12")).thenReturn(Optional.of(row));
        EmployeeAccountEntity acc = new EmployeeAccountEntity();
        acc.setId(277L);
        acc.setYhid(12476L);
        when(employeeAccountRepository.findById(277L)).thenReturn(Optional.of(acc));
        HrEmployeeEntity hr = new HrEmployeeEntity();
        hr.setId(12476L);
        hr.setIdentifier(HR_IDENTIFIER);
        when(hrEmployeeRepository.findById(12476L)).thenReturn(Optional.of(hr));
        when(hrEmployeePostRepository.findActiveWithDeptByYhid(12476L)).thenReturn(List.of());

        var res = service.resolveSalesman(AID, "12");
        assertEquals(HR_IDENTIFIER, res.getSalesmanId());
        assertNull(res.getSalesmanDeptId());
    }

    private void stubInviter(long accountId, long yhid, String identifier, long bmid) {
        EmployeeAccountEntity acc = new EmployeeAccountEntity();
        acc.setId(accountId);
        acc.setYhid(yhid);
        when(employeeAccountRepository.findById(accountId)).thenReturn(Optional.of(acc));
        HrEmployeeEntity hr = new HrEmployeeEntity();
        hr.setId(yhid);
        hr.setIdentifier(identifier);
        when(hrEmployeeRepository.findById(yhid)).thenReturn(Optional.of(hr));
        HrEmployeePostEntity post = new HrEmployeePostEntity();
        post.setId(1L);
        post.setYhid(yhid);
        post.setBmid(bmid);
        post.setRsStatus(1);
        when(hrEmployeePostRepository.findActiveWithDeptByYhid(yhid)).thenReturn(List.of(post));
    }

    private void stubActivityOnly() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(AID);
        when(activityRepository.findById(AID)).thenReturn(Optional.of(activity));
    }

    private void stubActivityAndViewer() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(AID);
        when(activityRepository.findById(AID)).thenReturn(Optional.of(activity));
        when(viewerRepository.existsByUsername("12")).thenReturn(true);
    }
}
