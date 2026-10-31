package com.zhibo.presence;

/**
 * 直播设置 · 用户数据房间级汇总（只含观众）。
 */
public record RoomUserDataStats(
        long liveViewerCount,
        long liveViewCount,
        long liveTotalSec,
        long replayViewerCount,
        long replayViewCount,
        long replayTotalSec,
        String trendStartTime,
        String trendEndTime) {

    public long liveAvgSec() {
        return avg(liveTotalSec, liveViewerCount);
    }

    public long replayAvgSec() {
        return avg(replayTotalSec, replayViewerCount);
    }

    public long combinedViewCount() {
        return liveViewCount + replayViewCount;
    }

    public long combinedTotalSec() {
        return liveTotalSec + replayTotalSec;
    }

    public long combinedAvgSec(long liveReplayUnionCount) {
        return avg(combinedTotalSec(), liveReplayUnionCount);
    }

    private static long avg(long totalSec, long viewers) {
        if (viewers <= 0) {
            return 0L;
        }
        return totalSec / viewers;
    }
}
