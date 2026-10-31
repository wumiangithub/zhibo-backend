package com.zhibo.admin.userdata;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.admin.activity.ActivityService;
import com.zhibo.admin.userdata.dto.ActivityUserDataResponse;
import com.zhibo.presence.PresenceEventRepository;
import com.zhibo.presence.RoomUserDataCalculator;
import com.zhibo.presence.RoomUserDataStats;
import com.zhibo.replay.RoomReplayWatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class UserDataService {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");

    private final ActivityService activityService;
    private final PresenceEventRepository presenceEventRepository;
    private final RoomReplayWatchRepository replayWatchRepository;
    private final Clock clock;

    public ActivityUserDataResponse userData(String activityId) {
        ActivityEntity activity = activityService.requireActivity(activityId);
        String roomId = activity.getId();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), SHANGHAI);

        RoomUserDataCalculator.Result result = RoomUserDataCalculator.compute(
                activity,
                presenceEventRepository.findByRoomId(roomId),
                replayWatchRepository.findByRoomId(roomId),
                now);
        RoomUserDataStats stats = result.stats();

        ActivityUserDataResponse response = new ActivityUserDataResponse();
        response.setId(activity.getId());

        ActivityUserDataResponse.Summary summary = response.getSummary();
        summary.setVisitorCount(stats.liveViewerCount());
        summary.setViewCount(stats.liveViewCount());
        summary.setAvgStayDurationSec(stats.liveAvgSec());

        response.getRows().add(row(
                "live_replay",
                result.liveReplayUnionCount(),
                stats.combinedViewCount(),
                stats.combinedAvgSec(result.liveReplayUnionCount())));
        response.getRows().add(row(
                "live",
                stats.liveViewerCount(),
                stats.liveViewCount(),
                stats.liveAvgSec()));
        response.getRows().add(row(
                "replay",
                stats.replayViewerCount(),
                stats.replayViewCount(),
                stats.replayAvgSec()));

        response.getTrendDefault().setStartTime(stats.trendStartTime());
        response.getTrendDefault().setEndTime(stats.trendEndTime());
        return response;
    }

    private static ActivityUserDataResponse.Row row(
            String type, long viewerCount, long viewCount, long avgSec) {
        ActivityUserDataResponse.Row row = new ActivityUserDataResponse.Row();
        row.setType(type);
        row.setViewerCount(viewerCount);
        row.setViewCount(viewCount);
        row.setAvgWatchDurationSec(avgSec);
        return row;
    }
}
