package com.zhibo.presence;

import com.zhibo.activity.RoomAttendanceEntity;
import com.zhibo.activity.RoomAttendanceRepository;
import com.zhibo.viewer.ViewerDisplayNames;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 进出双写：名单 {@code room_attendance} + 流水 {@code room_presence_event}。
 * <p>
 * mid 已存在 → 整次幂等。名单按未闭合 {@code socket_id} 决定在线；事件按真实时间落库。
 * 游客 / 无法认人的 userId：不写任何 {@code room_*}。
 * 观众 Join 的昵称 / 头像从 {@code viewer} 表解析（不认回调 context.nickname）。
 */
@Service
@RequiredArgsConstructor
public class PresenceWriteService {

    private final RoomAttendanceRepository attendanceRepository;
    private final PresenceEventRepository eventRepository;
    private final PresenceEventInserter eventInserter;
    private final ViewerRepository viewerRepository;

    public enum LeaveRosterOutcome {
        UPDATED,
        SKIPPED,
        MISSING,
        STALE
    }

    public record JoinResult(Long attendanceId, boolean rosterUpdated, boolean eventInserted, boolean midDuplicate) {
    }

    public record LeaveResult(LeaveRosterOutcome roster, boolean eventInserted, boolean midDuplicate) {
    }

    @Transactional
    public JoinResult applyJoin(
            String roomId,
            String userId,
            LocalDateTime eventTime,
            String nickname,
            String avatarUrl,
            String mid,
            String source,
            String socketId) {
        if (!PresenceUserIds.mayWriteRoom(userId)) {
            return new JoinResult(null, false, false, false);
        }
        if (isMidDuplicate(mid)) {
            return attendanceRepository.findByRoomIdAndUserId(roomId, userId)
                    .map(row -> new JoinResult(row.getId(), false, false, true))
                    .orElseGet(() -> new JoinResult(null, false, false, true));
        }

        String resolvedNick = nickname;
        String resolvedAvatar = avatarUrl;
        Long vid = PresenceUserIds.viewerId(userId);
        if (vid != null) {
            ViewerProfile profile = resolveViewerProfile(vid);
            resolvedNick = profile.nickname();
            if (profile.avatarUrl() != null) {
                resolvedAvatar = profile.avatarUrl();
            }
        }

        boolean eventInserted = eventInserter.insert(
                roomId, userId, PresenceEventEntity.TYPE_JOIN, eventTime, mid, source, socketId);

        PresenceEventEntity current = event(
                roomId, userId, PresenceEventEntity.TYPE_JOIN, eventTime, mid, source, socketId);
        PresenceSocketRoster.State state = PresenceSocketRoster.compute(mergedEvents(roomId, userId, current));

        RoomAttendanceEntity existing = attendanceRepository.findByRoomIdAndUserId(roomId, userId).orElse(null);
        return applyJoinRoster(
                existing, roomId, userId, eventTime, resolvedNick, resolvedAvatar, state, eventInserted);
    }

    @Transactional
    public LeaveResult applyLeave(
            String roomId,
            String userId,
            LocalDateTime eventTime,
            LocalDateTime expectedEnterTime,
            String mid,
            String source,
            String socketId) {
        if (!PresenceUserIds.mayWriteRoom(userId)) {
            return new LeaveResult(LeaveRosterOutcome.SKIPPED, false, false);
        }
        if (isMidDuplicate(mid)) {
            return new LeaveResult(LeaveRosterOutcome.SKIPPED, false, true);
        }

        boolean eventInserted = eventInserter.insert(
                roomId, userId, PresenceEventEntity.TYPE_LEAVE, eventTime, mid, source, socketId);

        PresenceEventEntity current = event(
                roomId, userId, PresenceEventEntity.TYPE_LEAVE, eventTime, mid, source, socketId);
        PresenceSocketRoster.State state = PresenceSocketRoster.compute(mergedEvents(roomId, userId, current));

        RoomAttendanceEntity row = attendanceRepository.findByRoomIdAndUserId(roomId, userId).orElse(null);
        LeaveRosterOutcome roster = applyLeaveRoster(row, eventTime, expectedEnterTime, state);
        return new LeaveResult(roster, eventInserted, false);
    }

    private ViewerProfile resolveViewerProfile(long viewerId) {
        ViewerEntity viewer = viewerRepository.findById(viewerId).orElse(null);
        if (viewer == null) {
            return new ViewerProfile(ViewerDisplayNames.displayNicknameForId(viewerId), null);
        }
        String nick = ViewerDisplayNames.displayNickname(viewer);
        String avatar = viewer.getAvatarUrl();
        if (avatar != null && avatar.isBlank()) {
            avatar = null;
        }
        return new ViewerProfile(truncate(nick, 128), truncate(avatar, 1024));
    }

    private JoinResult applyJoinRoster(
            RoomAttendanceEntity existing,
            String roomId,
            String userId,
            LocalDateTime eventTime,
            String nickname,
            String avatarUrl,
            PresenceSocketRoster.State state,
            boolean eventInserted) {
        LocalDateTime enter = state.latestOpenJoin() != null ? state.latestOpenJoin() : eventTime;
        if (existing == null) {
            RoomAttendanceEntity row = newRoom(roomId, userId, enter, nickname, avatarUrl);
            attendanceRepository.save(row);
            return new JoinResult(row.getId(), true, eventInserted, false);
        }
        boolean changed = false;
        if (existing.getLeaveTime() != null) {
            existing.setEnterTime(enter);
            existing.setLeaveTime(null);
            changed = true;
        } else if (enter.isAfter(existing.getEnterTime())) {
            existing.setEnterTime(enter);
            changed = true;
        }
        boolean acceptProfile = !eventTime.isBefore(existing.getEnterTime());
        if (acceptProfile && nickname != null) {
            existing.setNickname(nickname);
            changed = true;
        }
        if (acceptProfile && avatarUrl != null) {
            existing.setAvatarUrl(avatarUrl);
            changed = true;
        }
        if (changed) {
            attendanceRepository.save(existing);
        }
        return new JoinResult(existing.getId(), changed, eventInserted, false);
    }

    private LeaveRosterOutcome applyLeaveRoster(
            RoomAttendanceEntity row,
            LocalDateTime eventTime,
            LocalDateTime expectedEnterTime,
            PresenceSocketRoster.State state) {
        if (row == null) {
            return LeaveRosterOutcome.MISSING;
        }
        if (expectedEnterTime != null && !row.getEnterTime().equals(expectedEnterTime)) {
            return LeaveRosterOutcome.MISSING;
        }
        if (state.online()) {
            return LeaveRosterOutcome.SKIPPED;
        }
        if (row.getLeaveTime() != null) {
            return LeaveRosterOutcome.SKIPPED;
        }
        if (eventTime.isBefore(row.getEnterTime()) && state.latestClosedLeave() == null) {
            return LeaveRosterOutcome.STALE;
        }
        LocalDateTime leave = state.latestClosedLeave() != null ? state.latestClosedLeave() : eventTime;
        row.setLeaveTime(leave);
        attendanceRepository.save(row);
        return LeaveRosterOutcome.UPDATED;
    }

    private List<PresenceEventEntity> mergedEvents(String roomId, String userId, PresenceEventEntity current) {
        List<PresenceEventEntity> stored = eventRepository.findByRoomIdAndUserIdOrderByEventTimeAscIdAsc(roomId, userId);
        List<PresenceEventEntity> events = new ArrayList<>(stored == null ? List.of() : stored);
        if (!contains(events, current)) {
            events.add(current);
        }
        return events;
    }

    private static boolean contains(List<PresenceEventEntity> events, PresenceEventEntity current) {
        String mid = current.getMid();
        if (mid != null) {
            for (PresenceEventEntity e : events) {
                if (mid.equals(e.getMid())) {
                    return true;
                }
            }
        }
        for (PresenceEventEntity e : events) {
            if (sameSocketEvent(e, current)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sameSocketEvent(PresenceEventEntity a, PresenceEventEntity b) {
        return eq(a.getType(), b.getType())
                && eq(a.getSocketId(), b.getSocketId())
                && eq(a.getEventTime(), b.getEventTime())
                && eq(a.getSource(), b.getSource());
    }

    private static boolean eq(Object a, Object b) {
        return a == null ? b == null : a.equals(b);
    }

    private boolean isMidDuplicate(String mid) {
        return mid != null && !mid.isBlank() && eventRepository.existsByMid(mid.trim());
    }

    private static PresenceEventEntity event(
            String roomId,
            String userId,
            String type,
            LocalDateTime eventTime,
            String mid,
            String source,
            String socketId) {
        PresenceEventEntity event = new PresenceEventEntity();
        event.setRoomId(roomId);
        event.setUserId(userId);
        event.setType(type);
        event.setEventTime(eventTime);
        event.setMid(mid == null || mid.isBlank() ? null : mid.trim());
        event.setSource(source);
        event.setSocketId(socketId == null || socketId.isBlank() ? null : socketId.trim());
        return event;
    }

    private static RoomAttendanceEntity newRoom(
            String roomId, String userId, LocalDateTime enterTime, String nickname, String avatarUrl) {
        RoomAttendanceEntity row = new RoomAttendanceEntity();
        row.setRoomId(roomId);
        row.setUserId(userId);
        row.setNickname(nickname);
        row.setAvatarUrl(avatarUrl);
        row.setEnterTime(enterTime);
        return row;
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    private record ViewerProfile(String nickname, String avatarUrl) {
    }
}
