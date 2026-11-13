package com.zhibo.admin.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityLiveSessionRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.dashboard.dto.RecentLivesResponse;
import com.zhibo.presence.PresenceEventEntity;
import com.zhibo.presence.PresenceEventRepository;
import com.zhibo.replay.RoomReplayWatchRepository;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DashboardRecentLivesServiceTest {

    /** 2026-11-13 10:00 Asia/Shanghai */
    private static final Instant FIXED = Instant.parse("2026-11-13T02:00:00Z");

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivityLiveSessionRepository liveSessionRepository;
    @Mock
    private PresenceEventRepository presenceEventRepository;
    @Mock
    private RoomReplayWatchRepository replayWatchRepository;
    @Mock
    private ViewerRepository viewerRepository;
    @Mock
    private com.zhibo.activity.cloud.ActivityCloudSyncService activityCloudSyncService;

    private DashboardRecentLivesService service;

    @BeforeEach
    void setUp() {
        service = new DashboardRecentLivesService(
                activityRepository,
                liveSessionRepository,
                presenceEventRepository,
                replayWatchRepository,
                viewerRepository,
                activityCloudSyncService,
                Clock.fixed(FIXED, java.time.ZoneId.of("UTC")));
        org.mockito.Mockito.lenient()
                .doNothing()
                .when(activityCloudSyncService)
                .syncQuietlyAsync(any(), any());
    }

    @Test
    void normalizePage_defaultsAndRejects() {
        assertEquals(1, DashboardRecentLivesService.normalizePage(null));
        assertEquals(2, DashboardRecentLivesService.normalizePage(2));
        assertThrows(IllegalArgumentException.class, () -> DashboardRecentLivesService.normalizePage(0));
    }

    @Test
    void normalizePageSize_defaultsAndCaps() {
        assertEquals(10, DashboardRecentLivesService.normalizePageSize(null));
        assertEquals(10, DashboardRecentLivesService.normalizePageSize(10));
        assertEquals(50, DashboardRecentLivesService.normalizePageSize(50));
        assertThrows(IllegalArgumentException.class, () -> DashboardRecentLivesService.normalizePageSize(0));
        assertThrows(IllegalArgumentException.class, () -> DashboardRecentLivesService.normalizePageSize(51));
    }

    @Test
    void recentLives_empty_ok() {
        LocalDateTime from = LocalDateTime.of(2026, 11, 11, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 14, 0, 0);
        when(activityRepository.countWithActualStartBetween(eq(from), eq(to))).thenReturn(0L);
        when(activityRepository.findRecentWithActualStart(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        RecentLivesResponse resp = service.recentLives(1, 10);

        assertTrue(resp.getList().isEmpty());
        assertEquals(1, resp.getPage());
        assertEquals(10, resp.getPageSize());
        assertEquals(0L, resp.getTotal());
        verify(activityRepository).findRecentWithActualStart(eq(PageRequest.of(0, 10)));
        verify(liveSessionRepository, never()).findByActivityIdIn(any());
        verify(presenceEventRepository, never()).findByRoomIdIn(any());
    }

    @Test
    void recentLives_recentDaysEnough_usesWindow() {
        LocalDateTime from = LocalDateTime.of(2026, 11, 11, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 14, 0, 0);
        when(activityRepository.countWithActualStartBetween(eq(from), eq(to))).thenReturn(15L);
        when(activityRepository.findRecentWithActualStartBetween(eq(from), eq(to), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 15L));

        RecentLivesResponse resp = service.recentLives(null, null);

        assertEquals(0, resp.getList().size());
        assertEquals(15L, resp.getTotal());
        verify(activityRepository)
                .findRecentWithActualStartBetween(eq(from), eq(to), eq(PageRequest.of(0, 10)));
    }

    @Test
    void recentLives_row_usesActualTimes_andNewUsersOnStartDay() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(hex32());
        activity.setTitle("场次A");
        activity.setCoverUrl("https://cdn.example/cover.jpg");
        activity.setState(3);
        activity.setStartTime("2026-11-10 09:00:00");
        activity.setEndTime("2026-11-10 11:00:00");
        activity.setLiveActualStartTime(LocalDateTime.of(2026, 11, 12, 19, 0, 0));
        activity.setLiveActualEndTime(LocalDateTime.of(2026, 11, 12, 21, 0, 0));

        LocalDateTime from = LocalDateTime.of(2026, 11, 11, 0, 0);
        LocalDateTime to = LocalDateTime.of(2026, 11, 14, 0, 0);
        when(activityRepository.countWithActualStartBetween(eq(from), eq(to))).thenReturn(1L);
        when(activityRepository.findRecentWithActualStart(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activity), PageRequest.of(0, 10), 1L));
        when(liveSessionRepository.findByActivityIdIn(eq(List.of(activity.getId()))))
                .thenReturn(List.of());
        PresenceEventEntity e1 = join("u1111111111111", "s1", "2026-11-12 19:10:00");
        e1.setRoomId(activity.getId());
        PresenceEventEntity e2 = leave("u1111111111111", "s1", "2026-11-12 19:40:00");
        e2.setRoomId(activity.getId());
        PresenceEventEntity e3 = join("u2222222222222", "s2", "2026-11-12 19:20:00");
        e3.setRoomId(activity.getId());
        PresenceEventEntity e4 = leave("u2222222222222", "s2", "2026-11-12 19:50:00");
        e4.setRoomId(activity.getId());
        when(presenceEventRepository.findByRoomIdIn(eq(List.of(activity.getId()))))
                .thenReturn(List.of(e1, e2, e3, e4));
        when(replayWatchRepository.findByRoomIdIn(eq(List.of(activity.getId()))))
                .thenReturn(List.of());

        ViewerEntity newOnDay = new ViewerEntity();
        newOnDay.setUsername("u1111111111111");
        newOnDay.setCreatedTime(LocalDateTime.of(2026, 11, 12, 8, 0, 0));
        ViewerEntity oldUser = new ViewerEntity();
        oldUser.setUsername("u2222222222222");
        oldUser.setCreatedTime(LocalDateTime.of(2026, 11, 1, 8, 0, 0));
        when(viewerRepository.findByUsernameIn(any())).thenReturn(List.of(newOnDay, oldUser));

        RecentLivesResponse resp = service.recentLives(null, null);
        assertEquals(1, resp.getList().size());
        assertEquals(1, resp.getPage());
        assertEquals(10, resp.getPageSize());
        assertEquals(1L, resp.getTotal());
        var row = resp.getList().get(0);
        assertEquals(activity.getId(), row.getId());
        assertEquals("场次A", row.getTitle());
        assertEquals("2026-11-12 19:00:00", row.getStartTime());
        assertEquals("2026-11-12 21:00:00", row.getEndTime());
        assertEquals(3, row.getState());
        assertEquals(2L, row.getRoomVisitorCount());
        assertEquals(2L, row.getRoomViewCount());
        assertEquals(2L, row.getWatchViewerCount());
        assertEquals(2L, row.getWatchViewCount());
        assertTrue(row.getAvgWatchDurationSec() > 0);
        assertEquals(2, row.getMaxOnlinePeak());
        assertEquals(1L, row.getNewUserCount());
        verify(activityCloudSyncService).syncQuietlyAsync(
                eq(activity),
                eq(com.zhibo.activity.cloud.ActivityCloudSyncService.Options.sessionsOnly()));
        verify(activityCloudSyncService, never()).syncQuietly(any(), any());
        verify(liveSessionRepository).findByActivityIdIn(eq(List.of(activity.getId())));
        verify(presenceEventRepository).findByRoomIdIn(eq(List.of(activity.getId())));
        verify(replayWatchRepository).findByRoomIdIn(eq(List.of(activity.getId())));
        verify(viewerRepository).findByUsernameIn(any());
        verify(activityRepository).findRecentWithActualStart(eq(PageRequest.of(0, 10)));
    }

    private static PresenceEventEntity join(String username, String socket, String time) {
        return event(username, socket, PresenceEventEntity.TYPE_JOIN, time);
    }

    private static PresenceEventEntity leave(String username, String socket, String time) {
        return event(username, socket, PresenceEventEntity.TYPE_LEAVE, time);
    }

    private static long nextId = 1L;

    private static PresenceEventEntity event(String username, String socket, String type, String time) {
        PresenceEventEntity e = new PresenceEventEntity();
        e.setId(nextId++);
        e.setUsername(username);
        e.setSocketId(socket);
        e.setType(type);
        e.setEventTime(LocalDateTime.parse(time.replace(' ', 'T')));
        e.setSource("callback");
        return e;
    }

    private static String hex32() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
