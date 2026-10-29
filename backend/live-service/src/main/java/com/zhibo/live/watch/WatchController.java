package com.zhibo.live.watch;

import com.zhibo.live.watch.dto.WatchActivityListResponse;
import com.zhibo.live.watch.dto.WatchProductListResponse;
import com.zhibo.live.watch.dto.WatchRecordListResponse;
import com.zhibo.live.watch.dto.WatchResponse;
import com.zhibo.live.watch.dto.WatchSdkResponse;
import com.zhibo.live.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 观看端。路径命名 D62：动作在前、单 id 末尾。 */
@RestController
@RequestMapping("/api/v1/watch")
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

    @GetMapping("/detail/{id}")
    public ApiResponse<WatchResponse> watch(
            @PathVariable String id,
            @RequestParam(required = false) String guestId,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) Long viewerId) {
        return ApiResponse.ok(watchService.watch(id, guestId, nickname, viewerId));
    }

    @GetMapping("/sdk/{id}")
    public ApiResponse<WatchSdkResponse> sdk(
            @PathVariable String id,
            @RequestParam(required = false) String guestId,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) Long viewerId) {
        return ApiResponse.ok(watchSdkService.sdk(id, guestId, nickname, viewerId));
    }

    /** 本场关联商品快照（小黄车）；无绑定 → 空 list。 */
    @GetMapping("/products/{id}")
    public ApiResponse<WatchProductListResponse> products(@PathVariable String id) {
        return ApiResponse.ok(watchService.listProducts(id));
    }

    /** 回放 id 列表，供登录观众 replay/start。 */
    @GetMapping("/records/{id}")
    public ApiResponse<WatchRecordListResponse> records(@PathVariable String id) {
        return ApiResponse.ok(watchService.listRecords(id));
    }
}
