package com.zhibo.live.callback;

import com.zhibo.presence.PresenceEventEntity;
import com.zhibo.presence.PresenceWriteService;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.util.VhallCallbackSignUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VhallPresenceCallbackServiceTest {

    private static final String ROOM = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
    private static final String SECRET = "callback-secret";

    @Mock
    private VhallProperties vhallProperties;
    @Mock
    private VhallRoomIdResolver roomIdResolver;
    @Mock
    private PresenceWriteService presenceWriteService;

    private VhallPresenceCallbackService service;

    @BeforeEach
    void setUp() {
        service = new VhallPresenceCallbackService(vhallProperties, roomIdResolver, presenceWriteService);
        when(vhallProperties.resolveCallbackSecret()).thenReturn(SECRET);
    }

    @Test
    void handle_joinDualWrites() {
        Map<String, Object> body = baseBody("Join", 1_800_000_000L);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));
        when(roomIdResolver.resolveRoomId("287793338")).thenReturn(Optional.of(ROOM));
        when(presenceWriteService.applyJoin(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PresenceWriteService.JoinResult("rec", true, true, false));

        assertEquals(VhallPresenceCallbackService.HandleResult.OK, service.handle(body));

        ArgumentCaptor<LocalDateTime> time = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(presenceWriteService).applyJoin(
                eq(ROOM),
                eq("visit_v1"),
                time.capture(),
                eq("小龙女"),
                isNull(),
                eq("mid-1"),
                eq(PresenceEventEntity.SOURCE_CALLBACK));
        assertEquals(LocalDateTime.of(2027, 1, 15, 16, 0, 0), time.getValue());
    }

    @Test
    void handle_badSignatureRejected() {
        Map<String, Object> body = baseBody("Join", 1_800_000_000L);
        body.put("signature", "deadbeef");

        assertEquals(VhallPresenceCallbackService.HandleResult.BAD_SIGNATURE, service.handle(body));
        verify(presenceWriteService, never()).applyJoin(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void handle_unknownWebinarStillOkWithoutWrite() {
        Map<String, Object> body = baseBody("Leave", 1_800_000_100L);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));
        when(roomIdResolver.resolveRoomId("287793338")).thenReturn(Optional.empty());

        assertEquals(VhallPresenceCallbackService.HandleResult.OK, service.handle(body));
        verify(presenceWriteService, never()).applyLeave(any(), any(), any(), any(), any(), any());
    }

    @Test
    void handle_leavePassesNullExpectedEnter() {
        Map<String, Object> body = baseBody("Leave", 1_800_000_100L);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));
        when(roomIdResolver.resolveRoomId("287793338")).thenReturn(Optional.of(ROOM));
        when(presenceWriteService.applyLeave(any(), any(), any(), any(), any(), any()))
                .thenReturn(new PresenceWriteService.LeaveResult(
                        PresenceWriteService.LeaveRosterOutcome.UPDATED, true, false));

        assertEquals(VhallPresenceCallbackService.HandleResult.OK, service.handle(body));
        verify(presenceWriteService).applyLeave(
                eq(ROOM),
                eq("visit_v1"),
                any(),
                isNull(),
                eq("mid-1"),
                eq(PresenceEventEntity.SOURCE_CALLBACK));
    }

    private static Map<String, Object> baseBody(String type, long time) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("method_id", 20);
        body.put("type", type);
        body.put("mid", "mid-1");
        body.put("sender_id", "visit_v1");
        body.put("webinar_id", 287793338);
        body.put("time", time);
        body.put("context", Map.of("nickname", "小龙女", "avatar", "", "role_name", 2));
        return body;
    }
}
