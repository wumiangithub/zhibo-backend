package com.zhibo.live.watch;

import com.zhibo.activity.AttendanceTimes;
import com.zhibo.live.viewer.ViewerAuthException;
import com.zhibo.live.viewer.ViewerAuthInterceptor;
import com.zhibo.live.watch.dto.ReplayHeartbeatRequest;
import com.zhibo.live.watch.dto.ReplayHeartbeatResponse;
import com.zhibo.live.watch.dto.ReplayStartRequest;
import com.zhibo.live.watch.dto.ReplayStartResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/watch/replay")
@RequiredArgsConstructor
public class ReplayWatchController {

    private final ReplayWatchService replayWatchService;

    @PostMapping("/start/{id}")
    public ApiResponse<ReplayStartResponse> start(
            @PathVariable String id,
            @Valid @RequestBody ReplayStartRequest request,
            HttpServletRequest httpRequest) {
        String socketId = replayWatchService.start(id, requireViewerId(httpRequest), request.getRecordId());
        return ApiResponse.ok(new ReplayStartResponse(socketId));
    }

    @PostMapping("/heartbeat/{id}")
    public ApiResponse<ReplayHeartbeatResponse> heartbeat(
            @PathVariable String id,
            @Valid @RequestBody ReplayHeartbeatRequest request,
            HttpServletRequest httpRequest) {
        var last = replayWatchService.heartbeat(id, requireViewerId(httpRequest), request.getSocketId());
        return ApiResponse.ok(new ReplayHeartbeatResponse(AttendanceTimes.format(last)));
    }

    private static long requireViewerId(HttpServletRequest request) {
        Long viewerId = (Long) request.getAttribute(ViewerAuthInterceptor.ATTR_VIEWER_ID);
        if (viewerId == null) {
            throw new ViewerAuthException(401, "未登录");
        }
        return viewerId;
    }
}
