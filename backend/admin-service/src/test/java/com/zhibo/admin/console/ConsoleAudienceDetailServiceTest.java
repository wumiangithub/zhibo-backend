package com.zhibo.admin.console;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityAudienceEntity;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityLiveSessionRepository;
import com.zhibo.activity.ActivityOrderRepository;
import com.zhibo.activity.ActivityProductClickRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.AttendanceTimes;
import com.zhibo.activity.RoomAttendanceEntity;
import com.zhibo.activity.RoomAttendanceRepository;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.console.dto.ConsoleAudienceDetailItem;
import com.zhibo.admin.console.dto.ConsoleAudienceDetailListResponse;
import com.zhibo.presence.PresenceEventEntity;
import com.zhibo.presence.PresenceEventRepository;
import com.zhibo.replay.RoomReplayWatchRepository;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsoleAudienceDetailServiceTest {

    private static final String ROOM_ID = "a1b2c3d4e5f67890abcdef1234567890";
    private static final String USER_A = "qk_u_aaaa1111";
    private static final String USER_B = "qk_u_bbbb2222";
    private static final String STAFF = "e12";

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private PresenceEventRepository presenceEventRepository;
    @Mock
    private RoomAttendanceRepository attendanceRepository;
    @Mock
    private RoomReplayWatchRepository replayWatchRepository;
    @Mock
    private ActivityLiveSessionRepository liveSessionRepository;
    @Mock
    private ActivityAudienceRepository audienceRepository;
    @Mock
    private ActivityProductClickRepository productClickRepository;
    @Mock
    private ActivityOrderRepository orderRepository;
    @Mock
    private RoomCommentRepository commentRepository;
    @Mock
    private ViewerRepository viewerRepository;
    @Mock
    private SysUserAccountRepository userRepository;
    @Mock
    private Clock clock;

    @InjectMocks
    private ConsoleAudienceDetailService service;

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
        lenient().when(productClickRepository.countByActivityAndUsernames(eq(ROOM_ID), anyCollection()))
                .thenReturn(List.<Object[]>of());
        lenient().when(orderRepository.countByActivityUsernameAndStatus(eq(ROOM_ID), anyCollection()))
                .thenReturn(List.<Object[]>of());
        lenient().when(orderRepository.aggregatePaidByUsernames(anyCollection(), any()))
                .thenReturn(List.<Object[]>of());
        lenient().when(commentRepository.countActiveByUsernames(eq(ROOM_ID), anyCollection()))
                .thenReturn(List.<Object[]>of());
        lenient().when(audienceRepository.findByActivityIdAndUsernameIn(eq(ROOM_ID), anyCollection()))
                .thenReturn(List.of());
        lenient().when(viewerRepository.findByUsernameIn(anyCollection())).thenReturn(List.of());
        lenient().when(userRepository.findAllById(anyCollection())).thenReturn(List.of());
    }

    @Test
    void list_onlyViewerJoins_excludesStaff_andComputesEnterTimes() {
        when(presenceEventRepository.findByRoomId(ROOM_ID)).thenReturn(List.of(
                join(USER_A, "s1", "2026-09-30 10:00:00"),
                join(USER_A, "s2", "2026-09-30 11:00:00"),
                leave(USER_A, "s1", "2026-09-30 10:30:00"),
                join(STAFF, "s9", "2026-09-30 10:05:00"),
                join(USER_B, "s3", "2026-09-30 10:20:00")));
        when(attendanceRepository.findByRoomId(ROOM_ID)).thenReturn(List.of(
                attendance(USER_A, "2026-09-30 11:00:00", null),
                attendance(USER_B, "2026-09-30 10:20:00", "2026-09-30 10:40:00")));

        ViewerEntity va = viewer(USER_A, "观众A", "13312344032");
        ViewerEntity vb = viewer(USER_B, "观众B", null);
        when(viewerRepository.findByUsernameIn(anyCollection())).thenReturn(List.of(va, vb));

        ActivityAudienceEntity aud = new ActivityAudienceEntity();
        aud.setActivityId(ROOM_ID);
        aud.setUsername(USER_A);
        aud.setEmployeeInviterId(100L);
        when(audienceRepository.findByActivityIdAndUsernameIn(eq(ROOM_ID), anyCollection()))
                .thenReturn(List.of(aud));
        SysUserAccountEntity emp = new SysUserAccountEntity();
        emp.setId(100L);
        emp.setUserName("张三");
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(emp));

        List<Object[]> localOrders = List.<Object[]>of(
                new Object[] {USER_A, 1, 2L},
                new Object[] {USER_A, 2, 1L});
        when(orderRepository.countByActivityUsernameAndStatus(eq(ROOM_ID), anyCollection()))
                .thenReturn(localOrders);
        List<Object[]> paidAll = List.<Object[]>of(new Object[] {USER_A, 3, new BigDecimal("99.50")});
        when(orderRepository.aggregatePaidByUsernames(anyCollection(), any())).thenReturn(paidAll);
        List<Object[]> clicks = List.<Object[]>of(new Object[] {USER_A, 5L});
        when(productClickRepository.countByActivityAndUsernames(eq(ROOM_ID), anyCollection()))
                .thenReturn(clicks);
        List<Object[]> comments = List.<Object[]>of(new Object[] {USER_A, 4L});
        when(commentRepository.countActiveByUsernames(eq(ROOM_ID), anyCollection()))
                .thenReturn(comments);

        ConsoleAudienceDetailListResponse resp = service.list(
                ROOM_ID, 1, 10, null, null, null, null, null, null);

        assertEquals(2, resp.getTotal());
        assertEquals(USER_A, resp.getList().get(0).getUsername());
        ConsoleAudienceDetailItem a = resp.getList().get(0);
        assertEquals("2026-09-30 10:00:00", a.getFirstEnterTime());
        assertEquals("2026-09-30 11:00:00", a.getLatestEnterTime());
        assertEquals(2, a.getEnterCount());
        assertTrue(a.isOnline());
        assertNull(a.getLeaveTime());
        assertEquals("张三", a.getFollowerName());
        assertEquals(100L, a.getFollowerEmployeeId());
        assertEquals("133****4032", a.getPhoneMasked());
        assertEquals(5, a.getProductClickCount());
        assertEquals(2, a.getPendingPayCount());
        assertEquals(1, a.getPurchaseCount());
        assertEquals(3, a.getTotalPurchaseCount());
        assertEquals("99.50", a.getTotalPurchaseAmount());
        assertEquals(4, a.getCommentCount());
        assertEquals(0, a.getCouponReceiveCount());

        ConsoleAudienceDetailItem b = resp.getList().get(1);
        assertEquals(USER_B, b.getUsername());
        assertFalse(b.isOnline());
        assertEquals("2026-09-30 10:40:00", b.getLeaveTime());
        assertNull(b.getFollowerName());
    }

    @Test
    void list_onlineFilter_andTerminalForceOffline() {
        when(presenceEventRepository.findByRoomId(ROOM_ID)).thenReturn(List.of(
                join(USER_A, "s1", "2026-09-30 10:00:00"),
                join(USER_B, "s2", "2026-09-30 10:10:00")));
        when(attendanceRepository.findByRoomId(ROOM_ID)).thenReturn(List.of(
                attendance(USER_A, "2026-09-30 10:00:00", null),
                attendance(USER_B, "2026-09-30 10:10:00", "2026-09-30 10:20:00")));

        ConsoleAudienceDetailListResponse online = service.list(
                ROOM_ID, 1, 10, null, null, "online", null, null, null);
        assertEquals(1, online.getTotal());
        assertEquals(USER_A, online.getList().get(0).getUsername());

        ActivityEntity ended = new ActivityEntity();
        ended.setId(ROOM_ID);
        ended.setState(3);
        when(activityRepository.findById(ROOM_ID)).thenReturn(Optional.of(ended));
        ConsoleAudienceDetailListResponse afterEnd = service.list(
                ROOM_ID, 1, 10, null, null, "online", null, null, null);
        assertEquals(0, afterEnd.getTotal());
    }

    @Test
    void exportCsv_hasBomAndMaskedPhone() {
        when(presenceEventRepository.findByRoomId(ROOM_ID)).thenReturn(List.of(
                join(USER_A, "s1", "2026-09-30 10:00:00")));
        when(attendanceRepository.findByRoomId(ROOM_ID)).thenReturn(List.of(
                attendance(USER_A, "2026-09-30 10:00:00", null)));
        when(viewerRepository.findByUsernameIn(anyCollection()))
                .thenReturn(List.of(viewer(USER_A, "观众A", "13312344032")));

        byte[] csv = service.exportCsv(ROOM_ID, null, null, null, null, null, null);
        assertEquals((byte) 0xEF, csv[0]);
        assertEquals((byte) 0xBB, csv[1]);
        assertEquals((byte) 0xBF, csv[2]);
        String text = new String(csv, 3, csv.length - 3, StandardCharsets.UTF_8);
        assertTrue(text.contains("133****4032"));
        assertTrue(text.contains(USER_A));
    }

    private static PresenceEventEntity join(String username, String socket, String time) {
        return event(username, socket, PresenceEventEntity.TYPE_JOIN, time);
    }

    private static PresenceEventEntity leave(String username, String socket, String time) {
        return event(username, socket, PresenceEventEntity.TYPE_LEAVE, time);
    }

    private static PresenceEventEntity event(String username, String socket, String type, String time) {
        PresenceEventEntity e = new PresenceEventEntity();
        e.setRoomId(ROOM_ID);
        e.setUsername(username);
        e.setSocketId(socket);
        e.setType(type);
        e.setEventTime(AttendanceTimes.parse(time, "eventTime"));
        e.setSource(PresenceEventEntity.SOURCE_CALLBACK);
        return e;
    }

    private static RoomAttendanceEntity attendance(String username, String enter, String leave) {
        RoomAttendanceEntity row = new RoomAttendanceEntity();
        row.setRoomId(ROOM_ID);
        row.setUsername(username);
        row.setEnterTime(AttendanceTimes.parse(enter, "enterTime"));
        if (leave != null) {
            row.setLeaveTime(AttendanceTimes.parse(leave, "leaveTime"));
        }
        return row;
    }

    private static ViewerEntity viewer(String username, String nickname, String phone) {
        ViewerEntity v = new ViewerEntity();
        v.setUsername(username);
        v.setNickname(nickname);
        v.setPhone(phone);
        return v;
    }
}
