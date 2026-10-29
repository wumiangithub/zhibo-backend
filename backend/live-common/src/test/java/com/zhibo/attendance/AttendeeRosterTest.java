package com.zhibo.attendance;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.AttendanceTimes;
import com.zhibo.activity.RoomAttendanceEntity;
import com.zhibo.activity.RoomAttendanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendeeRosterTest {

    private static final String ROOM_ID = "a1b2c3d4e5f67890abcdef1234567890";
    private static final String PROVIDER_ROOM = "1001";

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private RoomAttendanceRepository attendanceRepository;

    @InjectMocks
    private AttendeeRoster roster;

    @BeforeEach
    void stubActivity() {
        lenient().when(activityRepository.findById(ROOM_ID)).thenReturn(Optional.of(activity(1)));
    }

    @Test
    void list_emptyLeaveTimeIsOnline() {
        RoomAttendanceEntity left = row(
                1L, "user-a", "观众A", "2026-09-30 11:00:00", "2026-09-30 11:30:00", "2026-09-30 11:00:01");
        RoomAttendanceEntity open = row(
                2L, "user-b", "观众B", "2026-09-30 10:30:00", null, "2026-09-30 10:30:01");
        when(attendanceRepository.findByRoomId(ROOM_ID)).thenReturn(List.of(left, open));

        AttendeePage all = roster.list(ROOM_ID, "all", 1, 20, null);

        assertEquals(1, all.activityState());
        assertEquals(2, all.total());
        assertEquals(1, all.page());
        assertEquals(List.of("user-a", "user-b"), all.list().stream().map(AttendeeRow::userId).toList());
        assertFalse(all.list().get(0).online());
        assertEquals("2026-09-30 11:30:00", all.list().get(0).leaveTime());
        assertTrue(all.list().get(1).online());
        assertNull(all.list().get(1).leaveTime());

        AttendeePage online = roster.list(ROOM_ID, "online", 1, 20, null);
        assertEquals(1, online.total());
        assertEquals("user-b", online.list().get(0).userId());

        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void list_emptyLeaveTimeDoesNotAskProvider() {
        RoomAttendanceEntity earlier = row(
                1L, "user-a", "观众A", "2026-09-30 10:00:00", null, "2026-09-30 10:00:01");
        RoomAttendanceEntity later = row(
                2L, "user-b", "观众B", "2026-09-30 11:00:00", null, "2026-09-30 11:00:01");
        when(attendanceRepository.findByRoomId(ROOM_ID)).thenReturn(List.of(earlier, later));

        AttendeePage all = roster.list(ROOM_ID, "all", 1, 20, null);

        assertEquals(List.of("user-b", "user-a"), all.list().stream().map(AttendeeRow::userId).toList());
        assertTrue(all.list().get(0).online());
        assertTrue(all.list().get(1).online());
        assertNull(earlier.getLeaveTime());
        assertNull(later.getLeaveTime());
    }

    @Test
    void list_allLeftAreOffline() {
        RoomAttendanceEntity left = row(
                1L, "user-a", "观众A", "2026-09-30 10:00:00", "2026-09-30 10:20:00", "2026-09-30 10:00:01");
        when(attendanceRepository.findByRoomId(ROOM_ID)).thenReturn(List.of(left));

        AttendeePage all = roster.list(ROOM_ID, "all", 1, 20, null);

        assertEquals(1, all.total());
        assertFalse(all.list().get(0).online());
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4, 5})
    void list_terminalStateAllOfflineWithoutChangingRows(int state) {
        when(activityRepository.findById(ROOM_ID)).thenReturn(Optional.of(activity(state)));
        RoomAttendanceEntity stillOpen = row(
                1L, "user-a", "观众A", "2026-09-30 10:00:00", null, "2026-09-30 10:00:01");
        RoomAttendanceEntity alreadyLeft = row(
                2L, "user-b", "观众B", "2026-09-30 10:30:00", "2026-09-30 11:00:00", "2026-09-30 10:30:01");
        when(attendanceRepository.findByRoomId(ROOM_ID)).thenReturn(List.of(stillOpen, alreadyLeft));

        AttendeePage all = roster.list(ROOM_ID, "all", 1, 20, null);
        AttendeePage online = roster.list(ROOM_ID, "online", 1, 20, null);
        AttendeePage offline = roster.list(ROOM_ID, "offline", 1, 20, null);

        assertEquals(state, all.activityState());
        assertEquals(2, all.total());
        assertTrue(all.list().stream().noneMatch(AttendeeRow::online));
        assertNull(stillOpen.getLeaveTime());
        assertEquals(0, online.total());
        assertEquals(2, offline.total());
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void list_rejectsPageBelowOne() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> roster.list(ROOM_ID, "all", 0, 20, null));
        assertEquals("page 不能小于 1", ex.getMessage());
    }

    @Test
    void list_pageIsOneBased() {
        RoomAttendanceEntity a = row(
                1L, "user-a", "观众A", "2026-09-30 11:00:00", null, "2026-09-30 11:00:01");
        RoomAttendanceEntity b = row(
                2L, "user-b", "观众B", "2026-09-30 10:00:00", null, "2026-09-30 10:00:01");
        RoomAttendanceEntity c = row(
                3L, "user-c", "观众C", "2026-09-30 09:00:00", null, "2026-09-30 09:00:01");
        when(attendanceRepository.findByRoomId(ROOM_ID)).thenReturn(List.of(a, b, c));

        AttendeePage page1 = roster.list(ROOM_ID, "all", 1, 2, null);
        AttendeePage page2 = roster.list(ROOM_ID, "all", 2, 2, null);

        assertEquals(3, page1.total());
        assertEquals(1, page1.page());
        assertEquals(2, page1.pageSize());
        assertEquals(List.of("user-a", "user-b"), page1.list().stream().map(AttendeeRow::userId).toList());
        assertEquals(2, page2.page());
        assertEquals(List.of("user-c"), page2.list().stream().map(AttendeeRow::userId).toList());
    }

    private static ActivityEntity activity(int state) {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(ROOM_ID);
        entity.setProvider("vhall");
        entity.setProviderRoomId(PROVIDER_ROOM);
        entity.setState(state);
        entity.setType(2);
        entity.setTitle("直播课");
        entity.setStartTime("2026-09-30 09:00:00");
        return entity;
    }

    private static RoomAttendanceEntity row(
            long id,
            String userId,
            String nickname,
            String enterTime,
            String leaveTime,
            String createdTime) {
        RoomAttendanceEntity row = new RoomAttendanceEntity();
        row.setId(id);
        row.setRoomId(ROOM_ID);
        row.setUserId(userId);
        row.setNickname(nickname);
        row.setEnterTime(AttendanceTimes.parse(enterTime, "enterTime"));
        if (leaveTime != null) {
            row.setLeaveTime(AttendanceTimes.parse(leaveTime, "leaveTime"));
        }
        row.setCreatedTime(LocalDateTime.parse(createdTime.replace(' ', 'T')));
        return row;
    }
}
