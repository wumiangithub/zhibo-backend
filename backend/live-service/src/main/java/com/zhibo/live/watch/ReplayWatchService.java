package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.AttendanceTimes;
import com.zhibo.common.UuidIds;
import com.zhibo.replay.RoomReplayWatchEntity;
import com.zhibo.replay.RoomReplayWatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class ReplayWatchService {

    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final WatchService watchService;
    private final RoomReplayWatchRepository replayWatchRepository;
    private final Clock clock;

    @Transactional
    public String start(String activityId, long viewerId, Long recordId) {
        if (recordId == null || recordId <= 0) {
            throw new IllegalArgumentException("recordId 须为正整数");
        }
        ActivityEntity activity = watchService.requireActivity(activityId);
        assertReplayAllowed(activity);
        LocalDateTime now = now();
        RoomReplayWatchEntity row = new RoomReplayWatchEntity();
        row.setRoomId(activity.getId());
        row.setViewerId(viewerId);
        row.setRecordId(recordId);
        row.setSocketId(UuidIds.newId());
        row.setStartTime(now);
        row.setLastReportTime(now);
        replayWatchRepository.save(row);
        return row.getSocketId();
    }

    @Transactional
    public LocalDateTime heartbeat(String activityId, long viewerId, String socketId) {
        String sid = socketId == null ? "" : socketId.trim();
        if (sid.isEmpty()) {
            throw new IllegalArgumentException("socketId 不能为空");
        }
        ActivityEntity activity = watchService.requireActivity(activityId);
        RoomReplayWatchEntity row = replayWatchRepository.findBySocketId(sid)
                .orElseThrow(() -> new IllegalArgumentException("回放观看记录不存在"));
        if (!activity.getId().equals(row.getRoomId()) || !Long.valueOf(viewerId).equals(row.getViewerId())) {
            throw new IllegalArgumentException("回放观看记录不存在");
        }
        LocalDateTime now = now();
        if (row.getLastReportTime() == null || now.isAfter(row.getLastReportTime())) {
            row.setLastReportTime(now);
            replayWatchRepository.save(row);
        }
        return row.getLastReportTime();
    }

    private void assertReplayAllowed(ActivityEntity activity) {
        if (Boolean.FALSE.equals(activity.getPlaybackEnabled())) {
            throw new IllegalArgumentException("回放未开启");
        }
        if (!"days".equals(activity.getPlaybackValidityType())) {
            return;
        }
        Integer days = activity.getPlaybackValidityDays();
        if (days == null || days < 1) {
            return;
        }
        LocalDateTime from = activity.getLiveActualEndTime();
        if (from == null) {
            from = parseNullable(activity.getEndTime());
        }
        if (from == null) {
            return;
        }
        if (now().isAfter(from.plusDays(days))) {
            throw new IllegalArgumentException("回放已过期");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), SHANGHAI);
    }

    private static LocalDateTime parseNullable(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AttendanceTimes.parse(raw, "endTime");
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
