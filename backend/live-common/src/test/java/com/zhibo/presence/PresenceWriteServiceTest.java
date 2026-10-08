package com.zhibo.presence;

import com.zhibo.activity.AttendanceTimes;
import com.zhibo.activity.RoomAttendanceEntity;
import com.zhibo.activity.RoomAttendanceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceWriteServiceTest {

    private static final String ROOM = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";

    @Mock
    private RoomAttendanceRepository attendanceRepository;
    @Mock
    private PresenceEventRepository eventRepository;
    @Mock
    private PresenceEventInserter eventInserter;

    @InjectMocks
    private PresenceWriteService writeService;

    @Test
    void join_staleEventStillInsertsEventButSkipsRoster() {
        RoomAttendanceEntity existing = row("rec-1", "guest-1", "2026-09-30 11:00:00", null);
        when(attendanceRepository.findByRoomIdAndUserId(ROOM, "guest-1"))
                .thenReturn(Optional.of(existing));
        when(eventInserter.insert(any(), any(), any(), any(), any(), any())).thenReturn(true);

        PresenceWriteService.JoinResult result = writeService.applyJoin(
                ROOM,
                "guest-1",
                AttendanceTimes.parse("2026-09-30 10:00:00", "t"),
                "昵称",
                null,
                "mid-1",
                PresenceEventEntity.SOURCE_CALLBACK);

        assertFalse(result.rosterUpdated());
        assertTrue(result.eventInserted());
        assertEquals("2026-09-30 11:00:00", AttendanceTimes.format(existing.getEnterTime()));
        verify(attendanceRepository, never()).save(any());
        verify(eventInserter).insert(
                eq(ROOM),
                eq("guest-1"),
                eq(PresenceEventEntity.TYPE_JOIN),
                eq(AttendanceTimes.parse("2026-09-30 10:00:00", "t")),
                eq("mid-1"),
                eq(PresenceEventEntity.SOURCE_CALLBACK));
    }

    @Test
    void join_midDuplicateIsFullyIdempotent() {
        when(eventRepository.existsByMid("mid-dup")).thenReturn(true);
        when(attendanceRepository.findByRoomIdAndUserId(ROOM, "guest-1"))
                .thenReturn(Optional.of(row("rec-1", "guest-1", "2026-09-30 11:00:00", null)));

        PresenceWriteService.JoinResult result = writeService.applyJoin(
                ROOM,
                "guest-1",
                AttendanceTimes.parse("2026-09-30 12:00:00", "t"),
                null,
                null,
                "mid-dup",
                PresenceEventEntity.SOURCE_CALLBACK);

        assertTrue(result.midDuplicate());
        assertFalse(result.eventInserted());
        assertFalse(result.rosterUpdated());
        verify(eventInserter, never()).insert(any(), any(), any(), any(), any(), any());
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void leave_missingStillInsertsEvent() {
        when(attendanceRepository.findByRoomIdAndUserId(ROOM, "guest-x")).thenReturn(Optional.empty());
        when(eventInserter.insert(any(), any(), any(), any(), any(), any())).thenReturn(true);

        PresenceWriteService.LeaveResult result = writeService.applyLeave(
                ROOM,
                "guest-x",
                AttendanceTimes.parse("2026-09-30 10:20:00", "t"),
                AttendanceTimes.parse("2026-09-30 10:00:00", "e"),
                null,
                PresenceEventEntity.SOURCE_CLIENT);

        assertEquals(PresenceWriteService.LeaveRosterOutcome.MISSING, result.roster());
        assertTrue(result.eventInserted());
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void leave_skippedDoesNotInsertEvent() {
        RoomAttendanceEntity existing = row("rec-1", "guest-1", "2026-09-30 10:00:00", "2026-09-30 10:05:00");
        when(attendanceRepository.findByRoomIdAndUserId(ROOM, "guest-1"))
                .thenReturn(Optional.of(existing));

        PresenceWriteService.LeaveResult result = writeService.applyLeave(
                ROOM,
                "guest-1",
                AttendanceTimes.parse("2026-09-30 10:20:00", "t"),
                AttendanceTimes.parse("2026-09-30 10:00:00", "e"),
                null,
                PresenceEventEntity.SOURCE_CLIENT);

        assertEquals(PresenceWriteService.LeaveRosterOutcome.SKIPPED, result.roster());
        assertFalse(result.eventInserted());
        assertEquals("2026-09-30 10:05:00", AttendanceTimes.format(existing.getLeaveTime()));
        verify(eventInserter, never()).insert(any(), any(), any(), any(), any(), any());
    }

    @Test
    void leave_duplicateEventInsertReturnsOkAsSkippedPathUsesInserterFalse() {
        RoomAttendanceEntity existing = row("rec-1", "guest-1", "2026-09-30 10:00:00", null);
        when(attendanceRepository.findByRoomIdAndUserId(ROOM, "guest-1"))
                .thenReturn(Optional.of(existing));
        when(attendanceRepository.save(any(RoomAttendanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        // 并发下流水已存在 / 撞唯一键 → inserter 返回 false，外层仍应成功（不抛）
        when(eventInserter.insert(any(), any(), any(), any(), any(), any())).thenReturn(false);

        PresenceWriteService.LeaveResult result = writeService.applyLeave(
                ROOM,
                "guest-1",
                AttendanceTimes.parse("2026-09-30 10:20:00", "t"),
                AttendanceTimes.parse("2026-09-30 10:00:00", "e"),
                null,
                PresenceEventEntity.SOURCE_CLIENT);

        assertEquals(PresenceWriteService.LeaveRosterOutcome.UPDATED, result.roster());
        assertFalse(result.eventInserted());
        verify(eventInserter).insert(
                eq(ROOM),
                eq("guest-1"),
                eq(PresenceEventEntity.TYPE_LEAVE),
                eq(AttendanceTimes.parse("2026-09-30 10:20:00", "t")),
                isNull(),
                eq(PresenceEventEntity.SOURCE_CLIENT));
    }

    @Test
    void join_firstTimeInsertsRosterAndEvent() {
        when(attendanceRepository.findByRoomIdAndUserId(ROOM, "guest-1")).thenReturn(Optional.empty());
        when(attendanceRepository.save(any(RoomAttendanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(eventInserter.insert(any(), any(), any(), any(), any(), any())).thenReturn(true);

        PresenceWriteService.JoinResult result = writeService.applyJoin(
                ROOM,
                "guest-1",
                AttendanceTimes.parse("2026-09-30 11:00:00", "t"),
                "昵称",
                "http://a",
                null,
                PresenceEventEntity.SOURCE_CLIENT);

        assertTrue(result.rosterUpdated());
        assertTrue(result.eventInserted());
        ArgumentCaptor<RoomAttendanceEntity> row = ArgumentCaptor.forClass(RoomAttendanceEntity.class);
        verify(attendanceRepository).save(row.capture());
        assertEquals(result.attendanceId(), row.getValue().getId());
        assertEquals("guest-1", row.getValue().getUserId());
        assertNull(row.getValue().getLeaveTime());
    }

    private static RoomAttendanceEntity row(String id, String userId, String enter, String leave) {
        RoomAttendanceEntity entity = new RoomAttendanceEntity();
        entity.setId(id);
        entity.setRoomId(ROOM);
        entity.setUserId(userId);
        entity.setEnterTime(AttendanceTimes.parse(enter, "enter"));
        if (leave != null) {
            entity.setLeaveTime(AttendanceTimes.parse(leave, "leave"));
        }
        return entity;
    }
}
