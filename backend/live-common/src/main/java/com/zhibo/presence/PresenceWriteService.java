package com.zhibo.presence;

import com.zhibo.activity.RoomAttendanceEntity;
import com.zhibo.activity.RoomAttendanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.zhibo.common.UuidIds;

import java.time.LocalDateTime;

/**
 * 进出双写：名单 {@code room_attendance} + 流水 {@code presence_event}。
 * <p>
 * mid 已存在 → 整次幂等（名单也不改）。名单按事件时间，过期不更新；事件仍按真实时间尝试落库。
 * 流水插入经 {@link PresenceEventInserter} 独立事务，重复 leave/enter 撞唯一键不会让整单 500。
 */
@Service
@RequiredArgsConstructor
public class PresenceWriteService {

    private final RoomAttendanceRepository attendanceRepository;
    private final PresenceEventRepository eventRepository;
    private final PresenceEventInserter eventInserter;

    public enum LeaveRosterOutcome {
        UPDATED,
        SKIPPED,
        MISSING,
        STALE
    }

    public record JoinResult(String attendanceId, boolean rosterUpdated, boolean eventInserted, boolean midDuplicate) {
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
            String source) {
        if (isMidDuplicate(mid)) {
            return attendanceRepository.findByRoomIdAndUserId(roomId, userId)
                    .map(row -> new JoinResult(row.getId(), false, false, true))
                    .orElseGet(() -> new JoinResult(null, false, false, true));
        }

        boolean eventInserted = eventInserter.insert(
                roomId, userId, PresenceEventEntity.TYPE_JOIN, eventTime, mid, source);

        RoomAttendanceEntity existing = attendanceRepository.findByRoomIdAndUserId(roomId, userId).orElse(null);
        if (existing == null) {
            RoomAttendanceEntity row = newRoom(roomId, userId, eventTime, nickname, avatarUrl);
            attendanceRepository.save(row);
            return new JoinResult(row.getId(), true, eventInserted, false);
        }

        boolean sameEnter = existing.getEnterTime().equals(eventTime);
        boolean alreadyOpen = existing.getLeaveTime() == null;
        if (sameEnter && alreadyOpen) {
            return new JoinResult(existing.getId(), false, eventInserted, false);
        }
        if (eventTime.isBefore(existing.getEnterTime())) {
            // 乱序过期：不改名单
            return new JoinResult(existing.getId(), false, eventInserted, false);
        }

        existing.setEnterTime(eventTime);
        existing.setLeaveTime(null);
        if (nickname != null) {
            existing.setNickname(nickname);
        }
        if (avatarUrl != null) {
            existing.setAvatarUrl(avatarUrl);
        }
        attendanceRepository.save(existing);
        return new JoinResult(existing.getId(), true, eventInserted, false);
    }

    /**
     * @param expectedEnterTime 客户端离开须与当前 enter_time 一致；回调传 {@code null} 表示只按时间比较
     */
    @Transactional
    public LeaveResult applyLeave(
            String roomId,
            String userId,
            LocalDateTime eventTime,
            LocalDateTime expectedEnterTime,
            String mid,
            String source) {
        if (isMidDuplicate(mid)) {
            return new LeaveResult(LeaveRosterOutcome.SKIPPED, false, true);
        }

        RoomAttendanceEntity row = attendanceRepository.findByRoomIdAndUserId(roomId, userId).orElse(null);
        LeaveRosterOutcome roster;

        if (row == null) {
            roster = LeaveRosterOutcome.MISSING;
        } else if (expectedEnterTime != null && !row.getEnterTime().equals(expectedEnterTime)) {
            roster = LeaveRosterOutcome.MISSING;
        } else if (eventTime.isBefore(row.getEnterTime())) {
            roster = LeaveRosterOutcome.STALE;
        } else if (row.getLeaveTime() != null) {
            roster = LeaveRosterOutcome.SKIPPED;
        } else {
            row.setLeaveTime(eventTime);
            attendanceRepository.save(row);
            roster = LeaveRosterOutcome.UPDATED;
        }

        // skipped（已有离开）不插事件；missing / stale / updated 仍按真实时间落 Leave
        boolean eventInserted = false;
        if (roster != LeaveRosterOutcome.SKIPPED) {
            eventInserted = eventInserter.insert(
                    roomId, userId, PresenceEventEntity.TYPE_LEAVE, eventTime, mid, source);
        }
        return new LeaveResult(roster, eventInserted, false);
    }

    private boolean isMidDuplicate(String mid) {
        return mid != null && !mid.isBlank() && eventRepository.existsByMid(mid.trim());
    }

    private static RoomAttendanceEntity newRoom(
            String roomId, String userId, LocalDateTime enterTime, String nickname, String avatarUrl) {
        RoomAttendanceEntity row = new RoomAttendanceEntity();
        row.setId(UuidIds.newId());
        row.setRoomId(roomId);
        row.setUserId(userId);
        row.setNickname(nickname);
        row.setAvatarUrl(avatarUrl);
        row.setEnterTime(enterTime);
        return row;
    }
}
