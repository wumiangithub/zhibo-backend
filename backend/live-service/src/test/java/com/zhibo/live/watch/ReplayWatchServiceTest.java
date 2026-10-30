package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRecordEntity;
import com.zhibo.activity.ActivityRecordRepository;
import com.zhibo.replay.RoomReplayWatchEntity;
import com.zhibo.replay.RoomReplayWatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReplayWatchServiceTest {

    private static final String ROOM = "a1b2c3d4e5f67890abcdef1234567890";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Instant NOW = Instant.parse("2026-10-29T04:00:00Z");

    @Mock
    private WatchService watchService;
    @Mock
    private RoomReplayWatchRepository replayWatchRepository;
    @Mock
    private ActivityRecordRepository activityRecordRepository;

    private ReplayWatchService service;

    @BeforeEach
    void setUp() {
        service = new ReplayWatchService(
                watchService, replayWatchRepository, activityRecordRepository, Clock.fixed(NOW, SHANGHAI));
    }

    @Test
    void start_insertsRowAndReturnsSocket() {
        when(watchService.requireActivity(ROOM)).thenReturn(replayable());
        when(activityRecordRepository.findByActivityIdAndProviderAndRecordId(ROOM, "vhall", 1001L))
                .thenReturn(Optional.of(readyRecord(1001L)));
        when(replayWatchRepository.save(any(RoomReplayWatchEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String socketId = service.start(ROOM, 12L, 1001L);

        assertEquals(32, socketId.length());
        ArgumentCaptor<RoomReplayWatchEntity> cap = ArgumentCaptor.forClass(RoomReplayWatchEntity.class);
        verify(replayWatchRepository).save(cap.capture());
        RoomReplayWatchEntity row = cap.getValue();
        assertEquals(ROOM, row.getRoomId());
        assertEquals(12L, row.getViewerId());
        assertEquals(1001L, row.getRecordId());
        assertEquals(socketId, row.getSocketId());
        assertEquals(LocalDateTime.of(2026, 10, 29, 12, 0), row.getStartTime());
        assertEquals(row.getStartTime(), row.getLastReportTime());
    }

    @Test
    void start_playbackDisabledRejected() {
        ActivityEntity activity = replayable();
        activity.setPlaybackEnabled(false);
        when(watchService.requireActivity(ROOM)).thenReturn(activity);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.start(ROOM, 12L, 1001L));
        assertEquals("回放未开启", ex.getMessage());
        verify(replayWatchRepository, never()).save(any());
    }

    @Test
    void start_daysExpiredRejected() {
        ActivityEntity activity = replayable();
        activity.setPlaybackValidityType("days");
        activity.setPlaybackValidityDays(7);
        activity.setLiveActualEndTime(LocalDateTime.of(2026, 10, 1, 12, 0));
        when(watchService.requireActivity(ROOM)).thenReturn(activity);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.start(ROOM, 12L, 1001L));
        assertEquals("回放已过期", ex.getMessage());
    }

    @Test
    void heartbeat_updatesLastReport() {
        RoomReplayWatchEntity row = existing("sock-1", 12L);
        when(watchService.requireActivity(ROOM)).thenReturn(replayable());
        when(replayWatchRepository.findBySocketId("sock-1")).thenReturn(Optional.of(row));
        when(replayWatchRepository.save(any(RoomReplayWatchEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LocalDateTime last = service.heartbeat(ROOM, 12L, "sock-1");

        assertEquals(LocalDateTime.of(2026, 10, 29, 12, 0), last);
        verify(replayWatchRepository).save(row);
    }

    @Test
    void heartbeat_wrongViewerRejected() {
        when(watchService.requireActivity(ROOM)).thenReturn(replayable());
        when(replayWatchRepository.findBySocketId("sock-1")).thenReturn(Optional.of(existing("sock-1", 99L)));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.heartbeat(ROOM, 12L, "sock-1"));
        assertEquals("回放观看记录不存在", ex.getMessage());
        verify(replayWatchRepository, never()).save(any());
        assertTrue(ex.getMessage().contains("不存在"));
    }

    private static ActivityRecordEntity readyRecord(long recordId) {
        ActivityRecordEntity row = new ActivityRecordEntity();
        row.setActivityId(ROOM);
        row.setProvider("vhall");
        row.setRecordId(recordId);
        row.setStatus(ActivityRecordEntity.STATUS_READY);
        row.setTosUrl("https://cdn.example.com/a.mp4");
        return row;
    }

    private static ActivityEntity replayable() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ROOM);
        activity.setProvider("vhall");
        activity.setPlaybackEnabled(true);
        activity.setPlaybackValidityType("forever");
        return activity;
    }

    private static RoomReplayWatchEntity existing(String socketId, long viewerId) {
        RoomReplayWatchEntity row = new RoomReplayWatchEntity();
        row.setId(1L);
        row.setRoomId(ROOM);
        row.setViewerId(viewerId);
        row.setRecordId(1001L);
        row.setSocketId(socketId);
        row.setStartTime(LocalDateTime.of(2026, 10, 29, 11, 0));
        row.setLastReportTime(LocalDateTime.of(2026, 10, 29, 11, 3));
        return row;
    }
}
