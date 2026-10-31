package com.zhibo.admin.userdata;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.admin.activity.ActivityService;
import com.zhibo.admin.userdata.dto.ActivityUserDataResponse;
import com.zhibo.presence.PresenceEventEntity;
import com.zhibo.presence.PresenceEventRepository;
import com.zhibo.replay.RoomReplayWatchEntity;
import com.zhibo.replay.RoomReplayWatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDataServiceTest {

    private static final String ID = "a1b2c3d4e5f67890abcdef1234567890";
    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    @Mock
    private ActivityService activityService;
    @Mock
    private PresenceEventRepository presenceEventRepository;
    @Mock
    private RoomReplayWatchRepository replayWatchRepository;

    private UserDataService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-29T04:00:00Z"), SHANGHAI);
        service = new UserDataService(
                activityService, presenceEventRepository, replayWatchRepository, clock);
    }

    @Test
    void summary_equals_live_row() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ID);
        activity.setState(3);
        activity.setLiveActualStartTime(LocalDateTime.of(2026, 10, 29, 10, 0));
        activity.setLiveActualEndTime(LocalDateTime.of(2026, 10, 29, 11, 0));
        when(activityService.requireActivity(ID)).thenReturn(activity);
        when(presenceEventRepository.findByRoomId(ID)).thenReturn(List.of(
                event("12", "Join", "s1", "2026-10-29 10:00:00"),
                event("12", "Leave", "s1", "2026-10-29 10:10:00")));
        RoomReplayWatchEntity replay = new RoomReplayWatchEntity();
        replay.setViewerId(99L);
        replay.setStartTime(LocalDateTime.of(2026, 10, 29, 12, 0));
        replay.setLastReportTime(LocalDateTime.of(2026, 10, 29, 12, 2));
        when(replayWatchRepository.findByRoomId(ID)).thenReturn(List.of(replay));

        ActivityUserDataResponse resp = service.userData(ID);

        ActivityUserDataResponse.Row live = resp.getRows().get(1);
        assertEquals("live", live.getType());
        assertEquals(live.getViewerCount(), resp.getSummary().getVisitorCount());
        assertEquals(live.getViewCount(), resp.getSummary().getViewCount());
        assertEquals(live.getAvgWatchDurationSec(), resp.getSummary().getAvgStayDurationSec());
        assertEquals(1L, live.getViewerCount());
        assertEquals(1L, live.getViewCount());
        assertEquals(600L, live.getAvgWatchDurationSec());
        assertEquals("replay", resp.getRows().get(2).getType());
        assertEquals(1L, resp.getRows().get(2).getViewerCount());
        assertEquals("2026-10-29 10:00:00", resp.getTrendDefault().getStartTime());
        assertEquals("2026-10-29 11:00:00", resp.getTrendDefault().getEndTime());
    }

    private static PresenceEventEntity event(String userId, String type, String socketId, String time) {
        PresenceEventEntity entity = new PresenceEventEntity();
        entity.setId((long) Math.abs((userId + type + socketId + time).hashCode()));
        entity.setUserId(userId);
        entity.setType(type);
        entity.setSocketId(socketId);
        entity.setEventTime(LocalDateTime.parse(time.replace(' ', 'T')));
        return entity;
    }
}
