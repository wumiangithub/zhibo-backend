package com.zhibo.live.watch;

import com.zhibo.live.viewer.ViewerJwtTokenService;
import com.zhibo.live.watch.dto.WatchActivityListResponse;
import com.zhibo.live.watch.dto.WatchChatHistoryResponse;
import com.zhibo.live.watch.dto.WatchProductListResponse;
import com.zhibo.live.watch.dto.WatchRecordListResponse;
import com.zhibo.live.watch.dto.WatchResponse;
import com.zhibo.live.watch.dto.WatchSdkResponse;
import com.zhibo.live.watch.dto.WatchSummaryResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
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
    private final WatchAccessService watchAccessService;
    private final WatchChatHistoryService watchChatHistoryService;
    private final ViewerJwtTokenService viewerJwtTokenService;

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

    /** 观看页壳摘要（D95）；须过售卖门禁。 */
    @GetMapping("/summary/{id}")
    public ApiResponse<WatchSummaryResponse> summary(
            @PathVariable String id,
            HttpServletRequest request) {
        Long viewerId = WatchAccessHeaders.tryViewerId(request, viewerJwtTokenService);
        watchAccessService.assertAllowed(id, viewerId, WatchAccessHeaders.activityAccess(request));
        return ApiResponse.ok(watchService.summary(id));
    }

    /** 观看 SDK 签名（D92/D95）；活动 id / viewerId 均在 Query；仅签名字段。 */
    @GetMapping("/sdk-sign")
    public ApiResponse<WatchSdkResponse> sdkSign(
            @RequestParam String id,
            @RequestParam(required = false) String nickname,
            @RequestParam(required = false) Long viewerId,
            HttpServletRequest request) {
        Long resolvedViewer = viewerId != null && viewerId > 0
                ? viewerId
                : WatchAccessHeaders.tryViewerId(request, viewerJwtTokenService);
        watchAccessService.assertAllowed(
                id, resolvedViewer, WatchAccessHeaders.activityAccess(request));
        return ApiResponse.ok(watchSdkService.sdk(id, nickname, resolvedViewer));
    }

    /** 本场关联商品快照（小黄车）；无绑定 → 空 list。 */
    @GetMapping("/products/{id}")
    public ApiResponse<WatchProductListResponse> products(@PathVariable String id) {
        return ApiResponse.ok(watchService.listProducts(id));
    }

    /** 回放 id 列表；须过售卖门禁（D90）。 */
    @GetMapping("/records/{id}")
    public ApiResponse<WatchRecordListResponse> records(
            @PathVariable String id,
            HttpServletRequest request) {
        Long viewerId = WatchAccessHeaders.tryViewerId(request, viewerJwtTokenService);
        watchAccessService.assertAllowed(id, viewerId, WatchAccessHeaders.activityAccess(request));
        return ApiResponse.ok(watchService.listRecords(id));
    }

    /** 回放页只读聊天历史（D91）；须过售卖门禁。 */
    @GetMapping("/chat-history/{id}")
    public ApiResponse<WatchChatHistoryResponse> chatHistory(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            HttpServletRequest request) {
        Long viewerId = WatchAccessHeaders.tryViewerId(request, viewerJwtTokenService);
        watchAccessService.assertAllowed(id, viewerId, WatchAccessHeaders.activityAccess(request));
        return ApiResponse.ok(watchChatHistoryService.list(id, page));
    }
}
