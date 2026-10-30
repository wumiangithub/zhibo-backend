package com.zhibo.live.watch;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRecordEntity;
import com.zhibo.activity.ActivityRecordRepository;
import com.zhibo.activity.PlaybackRules;
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
    private final ActivityRecordRepository activityRecordRepository;
    private final Clock clock;

    @Transactional
    public String start(String activityId, long viewerId, Long recordId) {
        if (recordId == null || recordId <= 0) {
            throw new IllegalArgumentException("recordId 须为正整数");
        }
        ActivityEntity activity = watchService.requireActivity(activityId);
        PlaybackRules.assertReplayAllowed(activity, now());
        requirePlayableRecord(activity, recordId);
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

    private void requirePlayableRecord(ActivityEntity activity, Long recordId) {
        String provider = activity.getProvider() != null ? activity.getProvider() : "vhall";
        ActivityRecordEntity row = activityRecordRepository
                .findByActivityIdAndProviderAndRecordId(activity.getId(), provider, recordId)
                .orElseThrow(() -> new IllegalArgumentException("回放不存在或未就绪"));
        if (!Integer.valueOf(ActivityRecordEntity.STATUS_READY).equals(row.getStatus())) {
            throw new IllegalArgumentException("回放不存在或未就绪");
        }
        String tos = row.getTosUrl();
        if (tos == null || tos.isBlank()) {
            throw new IllegalArgumentException("回放不存在或未就绪");
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), SHANGHAI);
    }
}
