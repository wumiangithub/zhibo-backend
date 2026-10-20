package com.zhibo.live.watch;

import com.zhibo.live.web.ApiResponse;
import com.zhibo.presence.PresenceTrend;
import com.zhibo.presence.PresenceTrendService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 进出三线走势。路径命名 D62：动作在前、单 id 末尾。 */
@RestController
@RequestMapping("/api/v1/watch")
@RequiredArgsConstructor
public class WatchPresenceTrendController {

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
