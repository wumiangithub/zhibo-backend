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

@RestController
@RequestMapping("/api/activities/{id}/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    public ApiResponse<ActivityStatsResponse> summary(@PathVariable String id) {
        return ApiResponse.ok(statsService.summary(id));
    }

    @GetMapping("/online")
    public ApiResponse<OnlineTrendResponse> online(
            @PathVariable String id,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        return ApiResponse.ok(statsService.onlineTrend(id, startTime, endTime));
    }
}
