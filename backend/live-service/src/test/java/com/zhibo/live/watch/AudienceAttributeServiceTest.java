package com.zhibo.live.watch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityAudienceEntity;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.live.watch.dto.AttributeAudienceRequest;
import com.zhibo.viewer.ViewerRepository;
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

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivityAudienceRepository audienceRepository;
    @Mock
    private ViewerRepository viewerRepository;

    private AudienceAttributeService service;

    @BeforeEach
    void setUp() {
        service = new AudienceAttributeService(
                activityRepository, audienceRepository, viewerRepository);
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
        when(audienceRepository.findByActivityIdAndViewerId(AID, 12L)).thenReturn(Optional.empty());
        when(audienceRepository.save(any())).thenAnswer(inv -> {
            ActivityAudienceEntity row = inv.getArgument(0);
            row.setId(99L);
            return row;
        });

        AttributeAudienceRequest req = new AttributeAudienceRequest();
        req.setInviterId(10001L);
        req.setInviterKind("employee");

        var res = service.attribute(AID, 12L, req);
        assertEquals(99L, res.getId());
        assertEquals(12L, res.getViewerId());

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
                        IllegalArgumentException.class, () -> service.attribute(AID, 12L, req));
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
                        IllegalArgumentException.class, () -> service.attribute(AID, 12L, req));
        assertEquals("inviterKind 须为 employee 或 audience", ex.getMessage());
    }

    private void stubActivityAndViewer() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(AID);
        when(activityRepository.findById(AID)).thenReturn(Optional.of(activity));
        when(viewerRepository.existsById(12L)).thenReturn(true);
    }
}
