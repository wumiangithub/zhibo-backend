package com.zhibo.admin.presence;

import com.zhibo.admin.web.ApiResponse;
import com.zhibo.presence.PresenceTrend;
import com.zhibo.presence.PresenceTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/activities")
@RequiredArgsConstructor
public class PresenceTrendController {

    private final PresenceTrendService presenceTrendService;

    @GetMapping("/presence-trend/{id}")
    public ApiResponse<PresenceTrend> trend(
            @PathVariable String id,
            @RequestParam String startTime,
            @RequestParam String endTime,
            @RequestParam(required = false) Integer bucketSeconds) {
        return ApiResponse.ok(presenceTrendService.trend(id, startTime, endTime, bucketSeconds));
    }
}
