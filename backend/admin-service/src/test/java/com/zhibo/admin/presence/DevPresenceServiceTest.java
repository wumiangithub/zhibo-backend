package com.zhibo.admin.presence;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.admin.activity.ActivityService;
import com.zhibo.admin.presence.dto.DevPresenceRequest;
import com.zhibo.admin.presence.dto.DevPresenceResponse;
import com.zhibo.presence.PresenceWriteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevPresenceServiceTest {

    @Mock
    private ActivityService activityService;
    @Mock
    private PresenceWriteService presenceWriteService;

    @InjectMocks
    private DevPresenceService service;

    private ActivityEntity activity;

    @BeforeEach
    void setUp() {
        activity = new ActivityEntity();
        activity.setId("a".repeat(32));
    }

    private void stubActivity() {
        when(activityService.requireActivity(anyString())).thenReturn(activity);
    }

    @Test
    void joinWritesWithStableSocketId() {
        stubActivity();
        DevPresenceRequest req = new DevPresenceRequest();
        req.setType("Join");
        req.setUsernames(List.of("qk_u_aaa", "qk_u_aaa", "qk_u_bbb"));

        when(presenceWriteService.applyJoin(
                anyString(), anyString(), any(), any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new PresenceWriteService.JoinResult(1L, true, true, false));

        DevPresenceResponse res = service.simulate(activity.getId(), req);
        assertEquals(2, res.getOk());
        assertEquals(0, res.getFailed());

        ArgumentCaptor<String> socketCap = ArgumentCaptor.forClass(String.class);
        verify(presenceWriteService).applyJoin(
                eq(activity.getId()),
                eq("qk_u_aaa"),
                any(),
                isNull(),
                isNull(),
                anyString(),
                eq("callback"),
                socketCap.capture());
        assertEquals("asim_qk_u_aaa", socketCap.getValue());
    }

    @Test
    void acceptsStaffUsername() {
        stubActivity();
        DevPresenceRequest req = new DevPresenceRequest();
        req.setType("Join");
        req.setUsernames(List.of("e10001"));

        when(presenceWriteService.applyJoin(
                anyString(), anyString(), any(), any(), any(), anyString(), anyString(), anyString()))
                .thenReturn(new PresenceWriteService.JoinResult(1L, true, true, false));

        DevPresenceResponse res = service.simulate(activity.getId(), req);
        assertEquals(1, res.getOk());
        assertEquals(0, res.getFailed());
        verify(presenceWriteService).applyJoin(
                eq(activity.getId()),
                eq("e10001"),
                any(),
                isNull(),
                isNull(),
                anyString(),
                eq("callback"),
                eq("asim_e10001"));
    }

    @Test
    void rejectsConsoleUsername() {
        stubActivity();
        DevPresenceRequest req = new DevPresenceRequest();
        req.setType("Leave");
        req.setUsernames(List.of("z10001"));

        DevPresenceResponse res = service.simulate(activity.getId(), req);
        assertEquals(0, res.getOk());
        assertEquals(1, res.getFailed());
        assertTrue(res.getItems().get(0).getError().contains("观众"));
    }

    @Test
    void socketIdForTruncates() {
        String longName = "a".repeat(80);
        String socket = DevPresenceService.socketIdFor(longName);
        assertEquals(64, socket.length());
        assertTrue(socket.startsWith("asim_"));
    }
}
