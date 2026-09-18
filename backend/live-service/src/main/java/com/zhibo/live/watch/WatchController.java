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

/**
 * 观看端进房 API。
 */
@RestController
@RequestMapping("/api/watch")
@RequiredArgsConstructor
public class WatchController {

    private final WatchService watchService;
    private final WatchSdkService watchSdkService;

    /**
     * 首页活动列表（精简字段）。须写在 {@code /{id}} 之前，避免把 activities 当 id。
     */
    @GetMapping("/activities")
    public ApiResponse<WatchActivityListResponse> activities(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer state) {
        return ApiResponse.ok(watchService.listActivities(page, pageSize, state));
    }

    /**
     * @param guestId  游客唯一 id（可选；不传则后端生成）
     * @param nickname 显示名（可选；默认「观众」）
     */
    @GetMapping("/{id}")
    public ApiResponse<WatchResponse> watch(
            @PathVariable long id,
            @RequestParam(required = false) String guestId,
            @RequestParam(required = false) String nickname) {
        return ApiResponse.ok(watchService.watch(id, guestId, nickname));
    }

    /**
     * 第四迭代默认观看入口：下发 JS SDK 初始化载荷（含服务端签名），前端自有容器播流。
     * Query 与 {@code /{id}} 相同。{@code /{id}/sdk} 两段，不会与单段 {@code /{id}} 冲突。
     */
    @GetMapping("/{id}/sdk")
    public ApiResponse<WatchSdkResponse> sdk(
            @PathVariable long id,
            @RequestParam(required = false) String guestId,
            @RequestParam(required = false) String nickname) {
        return ApiResponse.ok(watchSdkService.sdk(id, guestId, nickname));
    }
}
