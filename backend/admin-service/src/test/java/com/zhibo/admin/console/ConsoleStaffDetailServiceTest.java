package com.zhibo.admin.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityAudienceEntity;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityLiveSessionRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.AttendanceTimes;
import com.zhibo.activity.RoomAttendanceEntity;
import com.zhibo.activity.RoomAttendanceRepository;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.console.dto.ConsoleStaffDetailItem;
import com.zhibo.admin.console.dto.ConsoleStaffDetailListResponse;
import com.zhibo.admin.org.EmployeeDeptResolver;
import com.zhibo.presence.PresenceEventEntity;
import com.zhibo.presence.PresenceEventRepository;
import com.zhibo.replay.RoomReplayWatchRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsoleStaffDetailServiceTest {

    private static final String ROOM_ID = "a1b2c3d4e5f67890abcdef1234567890";
    private static final String USER_A = "qk_u_aaaa1111";
    private static final String USER_B = "qk_u_bbbb2222";
    private static final String USER_C = "qk_u_cccc3333";

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivityAudienceRepository audienceRepository;
    @Mock
    private PresenceEventRepository presenceEventRepository;
    @Mock
    private RoomAttendanceRepository attendanceRepository;
    @Mock
    private RoomReplayWatchRepository replayWatchRepository;
    @Mock
    private ActivityLiveSessionRepository liveSessionRepository;
    @Mock
    private RoomCommentRepository commentRepository;
    @Mock
    private SysUserAccountRepository userRepository;
    @Mock
    private EmployeeDeptResolver employeeDeptResolver;
    @Mock
    private Clock clock;

    @InjectMocks
    private ConsoleStaffDetailService service;

    @BeforeEach
    void stubBasics() {
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ROOM_ID);
        activity.setState(1);
        lenient().when(activityRepository.findById(ROOM_ID)).thenReturn(Optional.of(activity));
        lenient().when(clock.instant()).thenReturn(Instant.parse("2026-09-30T12:00:00Z"));
        lenient().when(clock.getZone()).thenReturn(ZoneId.of("Asia/Shanghai"));
        lenient().when(liveSessionRepository.findByActivityId(ROOM_ID)).thenReturn(List.of());
        lenient().when(replayWatchRepository.findByRoomId(ROOM_ID)).thenReturn(List.of());
        lenient().when(commentRepository.countActiveByUsernames(eq(ROOM_ID), anyCollection()))
                .thenReturn(List.<Object[]>of());
        lenient().when(userRepository.findAllById(anyCollection())).thenReturn(List.of());
        lenient().when(employeeDeptResolver.resolvePrimaryBatch(anyCollection())).thenReturn(Map.of());
        lenient().when(attendanceRepository.findByRoomId(ROOM_ID)).thenReturn(List.of());
        lenient().when(presenceEventRepository.findByRoomId(ROOM_ID)).thenReturn(List.of());
    }

    @Test
    void list_inviteRequiresJoin_liveWatchSamePool_avgAndComments() {
        when(audienceRepository.findByActivityId(ROOM_ID)).thenReturn(List.of(
                audience(USER_A, 100L),
                audience(USER_B, 100L),
                audience(USER_C, 100L),
                audience("qk_u_dddd4444", 200L)));
        when(presenceEventRepository.findByRoomId(ROOM_ID)).thenReturn(List.of(
                join(USER_A, "s1", "2026-09-30 10:00:00"),
                leave(USER_A, "s1", "2026-09-30 10:30:00"),
                join(USER_B, "s2", "2026-09-30 10:10:00"),
                join("e12", "s9", "2026-09-30 10:05:00")));
        when(attendanceRepository.findByRoomId(ROOM_ID)).thenReturn(List.of(
                attendance(USER_A, "2026-09-30 10:00:00", "2026-09-30 10:30:00"),
                attendance(USER_B, "2026-09-30 10:10:00", null)));
        when(commentRepository.countActiveByUsernames(eq(ROOM_ID), anyCollection()))
                .thenReturn(List.<Object[]>of(
                        new Object[] {USER_A, 3L},
                        new Object[] {USER_C, 2L}));

        SysUserAccountEntity emp100 = emp(100L, "张三");
        SysUserAccountEntity emp200 = emp(200L, "李四");
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(emp100, emp200));
        when(employeeDeptResolver.resolvePrimaryBatch(anyCollection()))
                .thenReturn(Map.of(
                        100L, new EmployeeDeptResolver.Dept(10L, "销售一部"),
                        200L, new EmployeeDeptResolver.Dept(20L, "销售二部")));

        ConsoleStaffDetailListResponse resp =
                service.list(ROOM_ID, 1, 10, null, null, null, null, null);

        assertEquals(2, resp.getTotal());
        ConsoleStaffDetailItem a = resp.getList().get(0);
        assertEquals(100L, a.getEmployeeId());
        assertEquals("张三", a.getEmployeeName());
        assertEquals(10L, a.getDeptId());
        assertEquals("销售一部", a.getDeptName());
        // A+B joined; C attributed but no Join → invite/liveWatch = 2
        assertEquals(2, a.getInviteCount());
        assertEquals(2, a.getLiveWatchCount());
        assertEquals(1, a.getOnlineCount());
        // comments: A(3)+C(2) among all attributed; B has 0
        assertEquals(5, a.getCommentCount());

        ConsoleStaffDetailItem b = resp.getList().get(1);
        assertEquals(200L, b.getEmployeeId());
        assertEquals(0, b.getInviteCount());
        assertEquals(0, b.getLiveWatchCount());
        assertEquals(0L, b.getAvgLiveWatchDurationSec());
        assertEquals(0, b.getOnlineCount());
        assertEquals(0, b.getCommentCount());
    }

    @Test
    void list_filters_employeeId_keyword_dept_and_avgZeroWhenNoWatchers() {
        when(audienceRepository.findByActivityId(ROOM_ID)).thenReturn(List.of(
                audience(USER_A, 100L),
                audience(USER_B, 200L)));
        when(presenceEventRepository.findByRoomId(ROOM_ID)).thenReturn(List.of(
                join(USER_A, "s1", "2026-09-30 10:00:00")));
        when(userRepository.findAllById(anyCollection()))
                .thenReturn(List.of(emp(100L, "张三"), emp(200L, "李四")));
        when(employeeDeptResolver.resolvePrimaryBatch(anyCollection()))
                .thenReturn(Map.of(
                        100L, new EmployeeDeptResolver.Dept(10L, "销售一部"),
                        200L, new EmployeeDeptResolver.Dept(20L, "销售二部")));

        ConsoleStaffDetailListResponse byId =
                service.list(ROOM_ID, 1, 10, 100L, null, null, null, null);
        assertEquals(1, byId.getTotal());
        assertEquals(100L, byId.getList().get(0).getEmployeeId());

        ConsoleStaffDetailListResponse byKw =
                service.list(ROOM_ID, 1, 10, null, "李", null, null, null);
        assertEquals(1, byKw.getTotal());
        assertEquals(200L, byKw.getList().get(0).getEmployeeId());
        assertEquals(0L, byKw.getList().get(0).getAvgLiveWatchDurationSec());

        ConsoleStaffDetailListResponse byDept =
                service.list(ROOM_ID, 1, 10, null, null, 10L, null, null);
        assertEquals(1, byDept.getTotal());
        assertEquals(100L, byDept.getList().get(0).getEmployeeId());

        assertThrows(
                IllegalArgumentException.class,
                () -> service.list(ROOM_ID, 1, 10, -1L, null, null, null, null));
    }

    @Test
    void list_terminalState_forcesOffline() {
        when(audienceRepository.findByActivityId(ROOM_ID)).thenReturn(List.of(audience(USER_A, 100L)));
        when(presenceEventRepository.findByRoomId(ROOM_ID))
                .thenReturn(List.of(join(USER_A, "s1", "2026-09-30 10:00:00")));
        when(attendanceRepository.findByRoomId(ROOM_ID))
                .thenReturn(List.of(attendance(USER_A, "2026-09-30 10:00:00", null)));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(emp(100L, "张三")));

        ActivityEntity ended = new ActivityEntity();
        ended.setId(ROOM_ID);
        ended.setState(3);
        when(activityRepository.findById(ROOM_ID)).thenReturn(Optional.of(ended));

        ConsoleStaffDetailListResponse resp =
                service.list(ROOM_ID, 1, 10, null, null, null, null, null);
        assertEquals(1, resp.getTotal());
        assertEquals(0, resp.getList().get(0).getOnlineCount());
        assertNull(resp.getList().get(0).getDeptId());
    }

    private static ActivityAudienceEntity audience(String username, long inviterId) {
        ActivityAudienceEntity a = new ActivityAudienceEntity();
        a.setActivityId(ROOM_ID);
        a.setUsername(username);
        a.setEmployeeInviterId(inviterId);
        return a;
    }

    private static SysUserAccountEntity emp(long id, String name) {
        SysUserAccountEntity u = new SysUserAccountEntity();
        u.setId(id);
        u.setUserName(name);
        return u;
    }

    private static PresenceEventEntity join(String username, String socketId, String time) {
        PresenceEventEntity e = new PresenceEventEntity();
        e.setRoomId(ROOM_ID);
        e.setUsername(username);
        e.setSocketId(socketId);
        e.setType(PresenceEventEntity.TYPE_JOIN);
        e.setEventTime(AttendanceTimes.parse(time, "eventTime"));
        e.setSource(PresenceEventEntity.SOURCE_CALLBACK);
        return e;
    }

    private static PresenceEventEntity leave(String username, String socketId, String time) {
        PresenceEventEntity e = join(username, socketId, time);
        e.setType(PresenceEventEntity.TYPE_LEAVE);
        return e;
    }

    private static RoomAttendanceEntity attendance(String username, String enter, String leave) {
        RoomAttendanceEntity a = new RoomAttendanceEntity();
        a.setRoomId(ROOM_ID);
        a.setUsername(username);
        a.setEnterTime(AttendanceTimes.parse(enter, "enterTime"));
        if (leave != null) {
            a.setLeaveTime(AttendanceTimes.parse(leave, "leaveTime"));
        }
        return a;
    }
}
