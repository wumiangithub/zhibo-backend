package com.zhibo.admin.stats;

import com.zhibo.admin.stats.dto.ActivityStatsResponse;
import com.zhibo.admin.stats.dto.OnlineTrendResponse;
import com.zhibo.admin.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活动观看统计 API。
 */
@RestController
@RequestMapping("/api/activities/{id}/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    /** 概览：热度 + 当前在线。 */
    @GetMapping
    public ApiResponse<ActivityStatsResponse> summary(@PathVariable long id) {
        return ApiResponse.ok(statsService.summary(id));
    }

    /**
     * 时间段在线人数趋势（分钟粒度）。
     * 微吼限制：endTime - startTime 不超过 1 天；接口约 1 次/分钟。
     */
    @GetMapping("/online")
    public ApiResponse<OnlineTrendResponse> online(
            @PathVariable long id,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        return ApiResponse.ok(statsService.onlineTrend(id, startTime, endTime));
    }
}
