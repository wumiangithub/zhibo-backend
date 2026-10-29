package com.zhibo.presence;

import com.zhibo.activity.AttendanceTimes;
import com.zhibo.activity.RoomAttendanceEntity;
import com.zhibo.activity.RoomAttendanceRepository;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
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

    private static final String ROOM = "a1b2c3d4e5f67890abcdef1234567890";
    private static final String SOCKET = "sock-1";
    private static final String VIEWER = "12";
    private static final String STAFF = "e10001";

    @Mock
    private RoomAttendanceRepository attendanceRepository;
    @Mock
    private PresenceEventRepository eventRepository;
    @Mock
    private PresenceEventInserter eventInserter;
    @Mock
    private ViewerRepository viewerRepository;

    @InjectMocks
    private PresenceWriteService writeService;

    @Test
    void join_guestUserIdSkippedEntirely() {
        PresenceWriteService.JoinResult result = writeService.applyJoin(
                ROOM,
                "guest1",
                AttendanceTimes.parse("2026-09-30 11:00:00", "t"),
                "随便",
                null,
                "mid-guest",
                PresenceEventEntity.SOURCE_CALLBACK,
                SOCKET);

        assertFalse(result.rosterUpdated());
        assertFalse(result.eventInserted());
        assertFalse(result.midDuplicate());
        verify(eventInserter, never()).insert(any(), any(), any(), any(), any(), any(), any());
        verify(attendanceRepository, never()).save(any());
        verify(viewerRepository, never()).findById(any());
    }

    @Test
    void join_viewerNicknameFromViewerPhoneNotContext() {
        ViewerEntity viewer = new ViewerEntity();
        viewer.setId(12L);
        viewer.setPhone("13612348079");
        when(viewerRepository.findById(12L)).thenReturn(Optional.of(viewer));
        when(attendanceRepository.findByRoomIdAndUserId(ROOM, VIEWER)).thenReturn(Optional.empty());
        when(attendanceRepository.save(any(RoomAttendanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(eventInserter.insert(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

        PresenceWriteService.JoinResult result = writeService.applyJoin(
                ROOM,
                VIEWER,
                AttendanceTimes.parse("2026-09-30 11:00:00", "t"),
                "微吼乱昵称",
                null,
                "mid-v",
                PresenceEventEntity.SOURCE_CALLBACK,
                SOCKET);

        assertTrue(result.rosterUpdated());
        ArgumentCaptor<RoomAttendanceEntity> row = ArgumentCaptor.forClass(RoomAttendanceEntity.class);
        verify(attendanceRepository).save(row.capture());
        assertEquals("用户8079", row.getValue().getNickname());
    }

    @Test
    void join_staleEventStillInsertsEventButSkipsRoster() {
        RoomAttendanceEntity existing = row(1L, VIEWER, "2026-09-30 11:00:00", null);
        when(viewerRepository.findById(12L)).thenReturn(Optional.empty());
        when(attendanceRepository.findByRoomIdAndUserId(ROOM, VIEWER))
                .thenReturn(Optional.of(existing));
        when(eventInserter.insert(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

        PresenceWriteService.JoinResult result = writeService.applyJoin(
                ROOM,
                VIEWER,
                AttendanceTimes.parse("2026-09-30 10:00:00", "t"),
                "昵称",
                null,
                "mid-1",
                PresenceEventEntity.SOURCE_CALLBACK,
                SOCKET);

        assertFalse(result.rosterUpdated());
        assertTrue(result.eventInserted());
        assertEquals("2026-09-30 11:00:00", AttendanceTimes.format(existing.getEnterTime()));
        verify(attendanceRepository, never()).save(any());
        verify(eventInserter).insert(
                eq(ROOM),
                eq(VIEWER),
                eq(PresenceEventEntity.TYPE_JOIN),
                eq(AttendanceTimes.parse("2026-09-30 10:00:00", "t")),
                eq("mid-1"),
                eq(PresenceEventEntity.SOURCE_CALLBACK),
                eq(SOCKET));
    }

    @Test
    void join_midDuplicateIsFullyIdempotent() {
        when(eventRepository.existsByMid("mid-dup")).thenReturn(true);
        when(attendanceRepository.findByRoomIdAndUserId(ROOM, VIEWER))
                .thenReturn(Optional.of(row(1L, VIEWER, "2026-09-30 11:00:00", null)));

        PresenceWriteService.JoinResult result = writeService.applyJoin(
                ROOM,
                VIEWER,
                AttendanceTimes.parse("2026-09-30 12:00:00", "t"),
                null,
                null,
                "mid-dup",
                PresenceEventEntity.SOURCE_CALLBACK,
                SOCKET);

        assertTrue(result.midDuplicate());
        assertFalse(result.eventInserted());
        assertFalse(result.rosterUpdated());
        verify(eventInserter, never()).insert(any(), any(), any(), any(), any(), any(), any());
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void leave_guestSkipped() {
        PresenceWriteService.LeaveResult result = writeService.applyLeave(
                ROOM,
                "guest1",
                AttendanceTimes.parse("2026-09-30 10:20:00", "t"),
                null,
                "mid-g",
                PresenceEventEntity.SOURCE_CALLBACK,
                SOCKET);

        assertEquals(PresenceWriteService.LeaveRosterOutcome.SKIPPED, result.roster());
        assertFalse(result.eventInserted());
        verify(eventInserter, never()).insert(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void leave_missingStillInsertsEvent() {
        when(attendanceRepository.findByRoomIdAndUserId(ROOM, STAFF)).thenReturn(Optional.empty());
        when(eventInserter.insert(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

        PresenceWriteService.LeaveResult result = writeService.applyLeave(
                ROOM,
                STAFF,
                AttendanceTimes.parse("2026-09-30 10:20:00", "t"),
                AttendanceTimes.parse("2026-09-30 10:00:00", "e"),
                null,
                PresenceEventEntity.SOURCE_CLIENT,
                SOCKET);

        assertEquals(PresenceWriteService.LeaveRosterOutcome.MISSING, result.roster());
        assertTrue(result.eventInserted());
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void leave_alreadyLeftStillInsertsEventButSkipsRoster() {
        RoomAttendanceEntity existing = row(1L, STAFF, "2026-09-30 10:00:00", "2026-09-30 10:05:00");
        when(attendanceRepository.findByRoomIdAndUserId(ROOM, STAFF))
                .thenReturn(Optional.of(existing));
        when(eventInserter.insert(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

        PresenceWriteService.LeaveResult result = writeService.applyLeave(
                ROOM,
                STAFF,
                AttendanceTimes.parse("2026-09-30 10:20:00", "t"),
                AttendanceTimes.parse("2026-09-30 10:00:00", "e"),
                null,
                PresenceEventEntity.SOURCE_CLIENT,
                SOCKET);

        assertEquals(PresenceWriteService.LeaveRosterOutcome.SKIPPED, result.roster());
        assertTrue(result.eventInserted());
        assertEquals("2026-09-30 10:05:00", AttendanceTimes.format(existing.getLeaveTime()));
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void leave_otherSocketStillOpenSkipsRoster() {
        RoomAttendanceEntity existing = row(1L, STAFF, "2026-09-30 10:00:00", null);
        when(attendanceRepository.findByRoomIdAndUserId(ROOM, STAFF))
                .thenReturn(Optional.of(existing));
        when(eventRepository.findByRoomIdAndUserIdOrderByEventTimeAscIdAsc(ROOM, STAFF))
                .thenReturn(List.of(event(STAFF, PresenceEventEntity.TYPE_JOIN, "sock-a", "2026-09-30 10:00:00")));
        when(eventInserter.insert(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

        PresenceWriteService.LeaveResult result = writeService.applyLeave(
                ROOM,
                STAFF,
                AttendanceTimes.parse("2026-09-30 10:20:00", "t"),
                AttendanceTimes.parse("2026-09-30 10:00:00", "e"),
                "mid-leave-b",
                PresenceEventEntity.SOURCE_CALLBACK,
                "sock-b");

        assertEquals(PresenceWriteService.LeaveRosterOutcome.SKIPPED, result.roster());
        assertTrue(result.eventInserted());
        assertNull(existing.getLeaveTime());
        verify(attendanceRepository, never()).save(any());
    }

    @Test
    void leave_duplicateEventInsertReturnsOkAsSkippedPathUsesInserterFalse() {
        RoomAttendanceEntity existing = row(1L, STAFF, "2026-09-30 10:00:00", null);
        when(attendanceRepository.findByRoomIdAndUserId(ROOM, STAFF))
                .thenReturn(Optional.of(existing));
        when(attendanceRepository.save(any(RoomAttendanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(eventInserter.insert(any(), any(), any(), any(), any(), any(), any())).thenReturn(false);

        PresenceWriteService.LeaveResult result = writeService.applyLeave(
                ROOM,
                STAFF,
                AttendanceTimes.parse("2026-09-30 10:20:00", "t"),
                AttendanceTimes.parse("2026-09-30 10:00:00", "e"),
                null,
                PresenceEventEntity.SOURCE_CLIENT,
                SOCKET);

        assertEquals(PresenceWriteService.LeaveRosterOutcome.UPDATED, result.roster());
        assertFalse(result.eventInserted());
        verify(eventInserter).insert(
                eq(ROOM),
                eq(STAFF),
                eq(PresenceEventEntity.TYPE_LEAVE),
                eq(AttendanceTimes.parse("2026-09-30 10:20:00", "t")),
                isNull(),
                eq(PresenceEventEntity.SOURCE_CLIENT),
                eq(SOCKET));
    }

    @Test
    void join_firstTimeInsertsRosterAndEvent_staffKeepsContextNick() {
        when(attendanceRepository.findByRoomIdAndUserId(ROOM, STAFF)).thenReturn(Optional.empty());
        when(attendanceRepository.save(any(RoomAttendanceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(eventInserter.insert(any(), any(), any(), any(), any(), any(), any())).thenReturn(true);

        PresenceWriteService.JoinResult result = writeService.applyJoin(
                ROOM,
                STAFF,
                AttendanceTimes.parse("2026-09-30 11:00:00", "t"),
                "助理甲",
                "http://a",
                null,
                PresenceEventEntity.SOURCE_CLIENT,
                SOCKET);

        assertTrue(result.rosterUpdated());
        assertTrue(result.eventInserted());
        ArgumentCaptor<RoomAttendanceEntity> row = ArgumentCaptor.forClass(RoomAttendanceEntity.class);
        verify(attendanceRepository).save(row.capture());
        assertEquals(result.attendanceId(), row.getValue().getId());
        assertEquals(STAFF, row.getValue().getUserId());
        assertEquals("助理甲", row.getValue().getNickname());
        assertNull(row.getValue().getLeaveTime());
        verify(viewerRepository, never()).findById(any());
    }

    private static RoomAttendanceEntity row(long id, String userId, String enter, String leave) {
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

    private static PresenceEventEntity event(String userId, String type, String socketId, String time) {
        PresenceEventEntity entity = new PresenceEventEntity();
        entity.setId((long) Math.abs((type + "-" + socketId + "-" + time).hashCode()));
        entity.setRoomId(ROOM);
        entity.setUserId(userId);
        entity.setType(type);
        entity.setSocketId(socketId);
        entity.setEventTime(AttendanceTimes.parse(time, "t"));
        entity.setSource(PresenceEventEntity.SOURCE_CALLBACK);
        return entity;
    }
}
