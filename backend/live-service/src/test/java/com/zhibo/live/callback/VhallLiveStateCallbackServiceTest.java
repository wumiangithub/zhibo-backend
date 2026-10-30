package com.zhibo.live.callback;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.VhallRoomIdResolver;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VhallLiveStateCallbackServiceTest {

    private static final String SECRET = "callback-secret";
    private static final String WEBINAR_ID = "123456";
    private static final String ACTIVITY_ID = "act-uuid-1";

    @Mock
    private VhallProperties vhallProperties;
    @Mock
    private VhallRoomIdResolver roomIdResolver;
    @Mock
    private ActivityRepository activityRepository;

    private VhallLiveStateCallbackService service;

    @BeforeEach
    void setUp() {
        service = new VhallLiveStateCallbackService(vhallProperties, roomIdResolver, activityRepository);
        lenient().when(vhallProperties.resolveCallbackSecret()).thenReturn(SECRET);
    }

    @Test
    void handle_startWritesState1() {
        ActivityEntity entity = activity(2);
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));

        Map<String, Object> body = liveStateBody(VhallLiveStateCallbackService.TYPE_START);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallLiveStateCallbackService.HandleResult.OK, service.handle(body));

        ArgumentCaptor<ActivityEntity> cap = ArgumentCaptor.forClass(ActivityEntity.class);
        verify(activityRepository).save(cap.capture());
        assertEquals(VhallLiveStateCallbackService.STATE_LIVE, cap.getValue().getState());
    }

    @Test
    void handle_endWritesState3() {
        ActivityEntity entity = activity(1);
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));

        Map<String, Object> body = liveStateBody(VhallLiveStateCallbackService.TYPE_END);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallLiveStateCallbackService.HandleResult.OK, service.handle(body));
        assertEquals(VhallLiveStateCallbackService.STATE_ENDED, entity.getState());
        verify(activityRepository).save(entity);
    }

    @Test
    void handle_sameStateStillWritesActualStartOnce() {
        ActivityEntity entity = activity(1);
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));

        Map<String, Object> body = liveStateBody(VhallLiveStateCallbackService.TYPE_START);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallLiveStateCallbackService.HandleResult.OK, service.handle(body));
        verify(activityRepository).save(entity);
        assertEquals(1, entity.getState());
        org.junit.jupiter.api.Assertions.assertNotNull(entity.getLiveActualStartTime());
    }

    @Test
    void handle_sameStateAndActualTimeSkipsSave() {
        ActivityEntity entity = activity(1);
        entity.setLiveActualStartTime(LocalDateTime.of(2026, 10, 1, 12, 0));
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));

        Map<String, Object> body = liveStateBody(VhallLiveStateCallbackService.TYPE_START);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallLiveStateCallbackService.HandleResult.OK, service.handle(body));
        verify(activityRepository, never()).save(any());
    }

    @Test
    void handle_badSignatureRejected() {
        Map<String, Object> body = liveStateBody(VhallLiveStateCallbackService.TYPE_START);
        body.put("signature", "deadbeef");

        assertEquals(VhallLiveStateCallbackService.HandleResult.BAD_SIGNATURE, service.handle(body));
        verify(activityRepository, never()).save(any());
    }

    @Test
    void handle_unknownWebinarStillOk() {
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.empty());
        Map<String, Object> body = liveStateBody(VhallLiveStateCallbackService.TYPE_START);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallLiveStateCallbackService.HandleResult.OK, service.handle(body));
        verify(activityRepository, never()).save(any());
    }

    @Test
    void handle_softDeletedStillOkNoSave() {
        ActivityEntity entity = activity(2);
        entity.setDeletedTime(LocalDateTime.of(2026, 10, 1, 12, 0));
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));

        Map<String, Object> body = liveStateBody(VhallLiveStateCallbackService.TYPE_START);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallLiveStateCallbackService.HandleResult.OK, service.handle(body));
        verify(activityRepository, never()).save(any());
    }

    @Test
    void handle_wrongMethodIgnored() {
        Map<String, Object> body = liveStateBody(VhallLiveStateCallbackService.TYPE_START);
        body.put("method_id", 20);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallLiveStateCallbackService.HandleResult.IGNORED, service.handle(body));
        verify(activityRepository, never()).save(any());
    }

    private static ActivityEntity activity(int state) {
        ActivityEntity e = new ActivityEntity();
        e.setId(ACTIVITY_ID);
        e.setProvider("vhall");
        e.setProviderRoomId(WEBINAR_ID);
        e.setState(state);
        return e;
    }

    private static Map<String, Object> liveStateBody(int type) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("method_id", VhallLiveStateCallbackService.METHOD_LIVE_STATE);
        body.put("refer", "vhall");
        body.put("time", 1_505_095_994L);
        body.put("webinar_id", Integer.parseInt(WEBINAR_ID));
        body.put("third_user_id", "123456");
        body.put("type", type);
        return body;
    }
}
