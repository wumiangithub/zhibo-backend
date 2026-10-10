package com.zhibo.live.watch;

import com.zhibo.live.watch.dto.WatchActivityListResponse;
import com.zhibo.live.watch.dto.WatchResponse;
import com.zhibo.live.watch.dto.WatchSdkResponse;
import com.zhibo.live.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/watch")
@RequiredArgsConstructor
public class WatchController {

    private final WatchService watchService;
    private final WatchSdkService watchSdkService;

    @GetMapping("/activities")
    public ApiResponse<WatchActivityListResponse> activities(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer state) {
        return ApiResponse.ok(watchService.listActivities(page, pageSize, state));
    }

    @GetMapping("/{id}")
    public ApiResponse<WatchResponse> watch(
            @PathVariable String id,
            @RequestParam(required = false) String guestId,
            @RequestParam(required = false) String nickname) {
        return ApiResponse.ok(watchService.watch(id, guestId, nickname));
    }

    @GetMapping("/{id}/sdk")
    public ApiResponse<WatchSdkResponse> sdk(
            @PathVariable String id,
            @RequestParam(required = false) String guestId,
            @RequestParam(required = false) String nickname) {
        return ApiResponse.ok(watchSdkService.sdk(id, guestId, nickname));
    }
}
