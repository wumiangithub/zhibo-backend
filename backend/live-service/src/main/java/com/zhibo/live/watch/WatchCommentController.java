package com.zhibo.live.watch;

import com.zhibo.live.viewer.ViewerAuthException;
import com.zhibo.live.viewer.ViewerAuthInterceptor;
import com.zhibo.live.watch.dto.DeleteCommentRequest;
import com.zhibo.live.watch.dto.ReportCommentRequest;
import com.zhibo.live.watch.dto.ReportCommentResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 观看页评论上报 / 软删；观众 id 只从 JWT 取。 */
@RestController
@RequestMapping("/api/v1/watch/comments")
@RequiredArgsConstructor
public class WatchCommentController {

    private final WatchCommentService watchCommentService;

    @PostMapping("/report/{id}")
    public ApiResponse<ReportCommentResponse> report(
            @PathVariable String id,
            @Valid @RequestBody ReportCommentRequest request,
            HttpServletRequest httpRequest) {
        long viewerId = requireViewerId(httpRequest);
        return ApiResponse.ok(watchCommentService.report(id, viewerId, request));
    }

    @PostMapping("/delete/{id}")
    public ApiResponse<ReportCommentResponse> delete(
            @PathVariable String id,
            @Valid @RequestBody DeleteCommentRequest request,
            HttpServletRequest httpRequest) {
        requireViewerId(httpRequest);
        return ApiResponse.ok(watchCommentService.softDelete(id, request));
    }

    private static long requireViewerId(HttpServletRequest request) {
        Long viewerId = (Long) request.getAttribute(ViewerAuthInterceptor.ATTR_VIEWER_ID);
        if (viewerId == null || viewerId <= 0) {
            throw new ViewerAuthException(401, "未登录");
        }
        return viewerId;
    }
}
