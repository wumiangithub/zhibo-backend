package com.zhibo.live.callback;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityLiveSessionEntity;
import com.zhibo.activity.ActivityLiveSessionRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.VhallRoomIdResolver;
import com.zhibo.activity.cloud.ActivityCloudSyncService;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.util.VhallCallbackSignUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @Mock
    private ActivityLiveSessionRepository sessionRepository;
    @Mock
    private ActivityCloudSyncService activityCloudSyncService;

    private VhallLiveStateCallbackService service;

    @BeforeEach
    void setUp() {
        service = new VhallLiveStateCallbackService(
                vhallProperties, roomIdResolver, activityRepository, sessionRepository, activityCloudSyncService);
        lenient().when(vhallProperties.resolveCallbackSecret()).thenReturn(SECRET);
        lenient().when(sessionRepository.findByActivityIdAndEndTimeIsNull(ACTIVITY_ID)).thenReturn(List.of());
        lenient().when(activityCloudSyncService.syncQuietly(any(), any()))
                .thenReturn(new ActivityCloudSyncService.Result(
                        true, false, false, 3, true, List.of(), false, false));
    }

    @Test
    void handle_startWritesState1AndOpensProvisionalSession() {
        ActivityEntity entity = activity(2);
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));

        Map<String, Object> body = liveStateBody(VhallLiveStateCallbackService.TYPE_START);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallLiveStateCallbackService.HandleResult.OK, service.handle(body));

        ArgumentCaptor<ActivityEntity> cap = ArgumentCaptor.forClass(ActivityEntity.class);
        verify(activityRepository).save(cap.capture());
        assertEquals(VhallLiveStateCallbackService.STATE_LIVE, cap.getValue().getState());
        assertNotNull(cap.getValue().getLiveActualStartTime());

        ArgumentCaptor<ActivityLiveSessionEntity> sessionCap =
                ArgumentCaptor.forClass(ActivityLiveSessionEntity.class);
        verify(sessionRepository).save(sessionCap.capture());
        assertEquals(ActivityLiveSessionEntity.PROVISIONAL_SWITCH_ID, sessionCap.getValue().getSwitchId());
        assertNotNull(sessionCap.getValue().getStartTime());
    }

    @Test
    void handle_endCoversActualEndAndClosesOpenSessionThenSyncs() {
        ActivityEntity entity = activity(1);
        entity.setLiveActualEndTime(LocalDateTime.of(2026, 10, 1, 10, 0));
        ActivityLiveSessionEntity open = new ActivityLiveSessionEntity();
        open.setId(9L);
        open.setActivityId(ACTIVITY_ID);
        open.setProvider("vhall");
        open.setSwitchId(ActivityLiveSessionEntity.PROVISIONAL_SWITCH_ID);
        open.setStartTime(LocalDateTime.of(2026, 10, 1, 9, 0));
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));
        when(sessionRepository.findByActivityIdAndEndTimeIsNull(ACTIVITY_ID)).thenReturn(List.of(open));

        Map<String, Object> body = liveStateBody(VhallLiveStateCallbackService.TYPE_END);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallLiveStateCallbackService.HandleResult.OK, service.handle(body));
        assertEquals(VhallLiveStateCallbackService.STATE_ENDED, entity.getState());
        // 末结覆盖（废止首次不覆盖）
        assertEquals(LocalDateTime.ofInstant(
                java.time.Instant.ofEpochSecond(1_505_095_994L),
                VhallLiveStateCallbackService.SHANGHAI), entity.getLiveActualEndTime());
        assertNotNull(open.getEndTime());
        verify(sessionRepository).save(open);
        verify(activityCloudSyncService).syncQuietly(
                eq(entity), eq(ActivityCloudSyncService.Options.sessionsOnly()));
    }

    @Test
    void handle_secondStartTakesEarlierActualStart() {
        ActivityEntity entity = activity(1);
        entity.setLiveActualStartTime(LocalDateTime.of(2026, 10, 1, 14, 0));
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));

        Map<String, Object> body = liveStateBody(VhallLiveStateCallbackService.TYPE_START);
        // time=1505095994 → 2017-09-11，远早于已有值
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallLiveStateCallbackService.HandleResult.OK, service.handle(body));
        assertTrue(entity.getLiveActualStartTime().isBefore(LocalDateTime.of(2026, 10, 1, 14, 0)));
        verify(activityRepository).save(entity);
    }

    @Test
    void handle_sameStateAndNoEarlierStartStillOpensSessionIfMissing() {
        ActivityEntity entity = activity(1);
        entity.setLiveActualStartTime(LocalDateTime.of(2017, 1, 1, 0, 0));
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));

        Map<String, Object> body = liveStateBody(VhallLiveStateCallbackService.TYPE_START);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallLiveStateCallbackService.HandleResult.OK, service.handle(body));
        verify(sessionRepository).save(any(ActivityLiveSessionEntity.class));
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

    @Test
    void handle_multiSessionSecondEndCoversEnd() {
        ActivityEntity entity = activity(1);
        entity.setLiveActualStartTime(LocalDateTime.of(2026, 11, 1, 10, 0));
        entity.setLiveActualEndTime(LocalDateTime.of(2026, 11, 1, 11, 0));
        ActivityLiveSessionEntity open = new ActivityLiveSessionEntity();
        open.setActivityId(ACTIVITY_ID);
        open.setProvider("vhall");
        open.setSwitchId(88L);
        open.setStartTime(LocalDateTime.of(2026, 11, 1, 14, 0));
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));
        when(sessionRepository.findByActivityIdAndEndTimeIsNull(ACTIVITY_ID))
                .thenReturn(new ArrayList<>(List.of(open)));

        Map<String, Object> body = liveStateBody(VhallLiveStateCallbackService.TYPE_END);
        // 2026-12-01 12:00:00 CST
        body.put("time", 1_796_097_600L);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallLiveStateCallbackService.HandleResult.OK, service.handle(body));
        assertEquals(LocalDateTime.of(2026, 12, 1, 12, 0), entity.getLiveActualEndTime());
        verify(activityCloudSyncService).syncQuietly(
                eq(entity), eq(ActivityCloudSyncService.Options.sessionsOnly()));
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
