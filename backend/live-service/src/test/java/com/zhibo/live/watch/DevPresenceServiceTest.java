package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.live.callback.VhallPresenceCallbackService;
import com.zhibo.live.callback.VhallPresenceCallbackService.HandleResult;
import com.zhibo.live.watch.dto.DevPresenceRequest;
import com.zhibo.live.watch.dto.DevPresenceResponse;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.util.VhallCallbackSignUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DevPresenceServiceTest {

    private static final String ROOM = "a1b2c3d4e5f67890abcdef1234567890";
    private static final String SECRET = "callback-secret";
    private static final String EMAIL = "guest-1@zhibo.local";
    private static final String SOCKET = "sock-dev-1";

    @Mock
    private WatchService watchService;
    @Mock
    private VhallProperties vhallProperties;
    @Mock
    private VhallPresenceCallbackService callbackService;

    @InjectMocks
    private DevPresenceService service;

    @Test
    void simulate_signsAndDelegatesToCallback() {
        when(watchService.requireActivity(ROOM)).thenReturn(vhallActivity());
        when(vhallProperties.resolveCallbackSecret()).thenReturn(SECRET);
        when(callbackService.handle(any())).thenReturn(HandleResult.OK);

        DevPresenceResponse response = service.simulate(ROOM, request("Join", EMAIL, SOCKET, "观众"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(callbackService).handle(captor.capture());
        Map<String, Object> body = captor.getValue();
        assertEquals(20, body.get("method_id"));
        assertEquals("Join", body.get("type"));
        assertEquals("287793338", body.get("webinar_id"));
        assertEquals(EMAIL, body.get("email"));
        assertEquals(SOCKET, body.get("socket_id"));
        assertEquals(response.getMid(), body.get("mid"));
        assertTrue(response.getMid().startsWith("dev"));
        assertTrue(VhallCallbackSignUtil.matches(SECRET, body, String.valueOf(body.get("signature"))));
        assertInstanceOf(Map.class, body.get("context"));
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) body.get("context");
        assertEquals("观众", context.get("nickname"));
    }

    @Test
    void simulate_blankSecretDoesNotCallCallback() {
        when(watchService.requireActivity(ROOM)).thenReturn(vhallActivity());
        when(vhallProperties.resolveCallbackSecret()).thenReturn("  ");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.simulate(ROOM, request("Leave", EMAIL, SOCKET, null)));
        assertEquals("未配置微吼回调密钥，无法模拟回调", ex.getMessage());
        verify(callbackService, never()).handle(any());
    }

    @Test
    void simulate_nonVhallRejected() {
        ActivityEntity activity = vhallActivity();
        activity.setProvider("aliyun");
        when(watchService.requireActivity(ROOM)).thenReturn(activity);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.simulate(ROOM, request("Join", EMAIL, SOCKET, null)));
        assertEquals("仅微吼活动可模拟上下线回调", ex.getMessage());
        verify(callbackService, never()).handle(any());
    }

    private static ActivityEntity vhallActivity() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ROOM);
        activity.setProvider("vhall");
        activity.setProviderRoomId("287793338");
        return activity;
    }

    private static DevPresenceRequest request(String type, String email, String socketId, String nickname) {
        DevPresenceRequest request = new DevPresenceRequest();
        request.setType(type);
        request.setEmail(email);
        request.setSocketId(socketId);
        request.setNickname(nickname);
        return request;
    }
}
