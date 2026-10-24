package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.AttendanceTimes;
import com.zhibo.live.watch.dto.EnterAttendanceRequest;
import com.zhibo.live.watch.dto.EnterAttendanceResponse;
import com.zhibo.live.watch.dto.LeaveAttendanceItem;
import com.zhibo.live.watch.dto.LeaveAttendanceRequest;
import com.zhibo.live.watch.dto.LeaveAttendanceResponse;
import com.zhibo.presence.PresenceEventEntity;
import com.zhibo.presence.PresenceWriteService;
import com.zhibo.presence.PresenceWriteService.JoinResult;
import com.zhibo.presence.PresenceWriteService.LeaveResult;
import com.zhibo.presence.PresenceWriteService.LeaveRosterOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final String ROOM_ID = "a1b2c3d4e5f67890abcdef1234567890";

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private PresenceWriteService presenceWriteService;

    @InjectMocks
    private AttendanceService attendanceService;

    @Test
    void enter_delegatesDualWrite() {
        when(activityRepository.findById(ROOM_ID)).thenReturn(Optional.of(activity()));
        when(presenceWriteService.applyJoin(
                eq(ROOM_ID),
                eq("guest-1"),
                eq(AttendanceTimes.parse("2026-09-30 11:00:00", "enterTime")),
                isNull(),
                isNull(),
                isNull(),
                eq(PresenceEventEntity.SOURCE_CLIENT)))
                .thenReturn(new JoinResult("rec-1", true, true, false));

        EnterAttendanceResponse response = attendanceService.enter(
                ROOM_ID, enterRequest("guest-1", "2026-09-30 11:00:00"));

        assertEquals("rec-1", response.getId());
    }

    @Test
    void enter_passesMidForIdempotency() {
        when(activityRepository.findById(ROOM_ID)).thenReturn(Optional.of(activity()));
        when(presenceWriteService.applyJoin(
                eq(ROOM_ID),
                eq("guest-1"),
                eq(AttendanceTimes.parse("2026-09-30 11:00:00", "enterTime")),
                isNull(),
                isNull(),
                eq("mid-enter-1"),
                eq(PresenceEventEntity.SOURCE_CLIENT)))
                .thenReturn(new JoinResult("rec-1", false, false, true));

        EnterAttendanceRequest request = enterRequest("guest-1", "2026-09-30 11:00:00");
        request.setMid("mid-enter-1");
        EnterAttendanceResponse response = attendanceService.enter(ROOM_ID, request);

        assertEquals("rec-1", response.getId());
    }

    @Test
    void leave_midDuplicateCountsSkipped() {
        when(activityRepository.findById(ROOM_ID)).thenReturn(Optional.of(activity()));
        when(presenceWriteService.applyLeave(
                eq(ROOM_ID),
                eq("guest-1"),
                eq(AttendanceTimes.parse("2026-09-30 10:20:00", "leaveTime")),
                eq(AttendanceTimes.parse("2026-09-30 10:00:00", "enterTime")),
                eq("mid-leave-1"),
                eq(PresenceEventEntity.SOURCE_CLIENT)))
                .thenReturn(new LeaveResult(LeaveRosterOutcome.SKIPPED, false, true));

        LeaveAttendanceItem one = item("guest-1", "2026-09-30 10:00:00", "2026-09-30 10:20:00");
        one.setMid("mid-leave-1");
        LeaveAttendanceResponse response = attendanceService.leave(ROOM_ID, leaveRequest(List.of(one)));

        assertEquals(0, response.getUpdated());
        assertEquals(1, response.getSkipped());
        assertEquals(0, response.getMissing());
    }

    @Test
    void leave_mapsRosterOutcomes() {
        when(activityRepository.findById(ROOM_ID)).thenReturn(Optional.of(activity()));
        when(presenceWriteService.applyLeave(
                eq(ROOM_ID),
                eq("guest-1"),
                eq(AttendanceTimes.parse("2026-09-30 10:20:00", "leaveTime")),
                eq(AttendanceTimes.parse("2026-09-30 10:00:00", "enterTime")),
                isNull(),
                eq(PresenceEventEntity.SOURCE_CLIENT)))
                .thenReturn(new LeaveResult(LeaveRosterOutcome.UPDATED, true, false));
        when(presenceWriteService.applyLeave(
                eq(ROOM_ID),
                eq("guest-2"),
                eq(AttendanceTimes.parse("2026-09-30 10:30:00", "leaveTime")),
                eq(AttendanceTimes.parse("2026-09-30 10:05:00", "enterTime")),
                isNull(),
                eq(PresenceEventEntity.SOURCE_CLIENT)))
                .thenReturn(new LeaveResult(LeaveRosterOutcome.SKIPPED, false, false));
        when(presenceWriteService.applyLeave(
                eq(ROOM_ID),
                eq("guest-3"),
                eq(AttendanceTimes.parse("2026-09-30 10:40:00", "leaveTime")),
                eq(AttendanceTimes.parse("2026-09-30 10:10:00", "enterTime")),
                isNull(),
                eq(PresenceEventEntity.SOURCE_CLIENT)))
                .thenReturn(new LeaveResult(LeaveRosterOutcome.MISSING, true, false));

        LeaveAttendanceResponse response = attendanceService.leave(ROOM_ID, leaveRequest(List.of(
                item("guest-1", "2026-09-30 10:00:00", "2026-09-30 10:20:00"),
                item("guest-2", "2026-09-30 10:05:00", "2026-09-30 10:30:00"),
                item("guest-3", "2026-09-30 10:10:00", "2026-09-30 10:40:00"))));

        assertEquals(1, response.getUpdated());
        assertEquals(1, response.getSkipped());
        assertEquals(1, response.getMissing());
    }

    @Test
    void leave_moreThan100IsBadRequestAndWritesNothing() {
        List<LeaveAttendanceItem> items = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            items.add(item("guest-" + i, "2026-09-30 10:00:00", "2026-09-30 10:20:00"));
        }

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                attendanceService.leave(ROOM_ID, leaveRequest(items)));

        assertEquals("items 不能超过100条", ex.getMessage());
        verify(activityRepository, never()).findById(any());
        verify(presenceWriteService, never()).applyLeave(any(), any(), any(), any(), any(), any());
    }

    private static ActivityEntity activity() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(ROOM_ID);
        entity.setProvider("vhall");
        entity.setState(1);
        entity.setType(2);
        entity.setTitle("直播课");
        entity.setStartTime("2026-09-30 09:00:00");
        return entity;
    }

    private static EnterAttendanceRequest enterRequest(String userId, String enterTime) {
        EnterAttendanceRequest request = new EnterAttendanceRequest();
        request.setUserId(userId);
        request.setEnterTime(enterTime);
        return request;
    }

    private static LeaveAttendanceRequest leaveRequest(List<LeaveAttendanceItem> items) {
        LeaveAttendanceRequest request = new LeaveAttendanceRequest();
        request.setItems(items);
        return request;
    }

    private static LeaveAttendanceItem item(String userId, String enterTime, String leaveTime) {
        LeaveAttendanceItem item = new LeaveAttendanceItem();
        item.setUserId(userId);
        item.setEnterTime(enterTime);
        item.setLeaveTime(leaveTime);
        return item;
    }
}
