package com.zhibo.live.watch;

import com.zhibo.live.viewer.ViewerAuthException;
import com.zhibo.live.viewer.ViewerAuthInterceptor;
import com.zhibo.live.watch.dto.DeleteCommentRequest;
import com.zhibo.live.watch.dto.ReportCommentRequest;
import com.zhibo.live.watch.dto.ReportCommentResponse;
import com.zhibo.live.watch.dto.UpsertQuestionAnswerRequest;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 观看页评论上报 / 提问回复 / 软删；观众 id 只从 JWT 取。 */
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
        String viewerUsername = ViewerAuthInterceptor.requireViewerUsername(httpRequest);
        return ApiResponse.ok(watchCommentService.report(id, viewerUsername, request));
    }

    /** 提问回复 upsert（D107）；任意已登录观众可上报，按微吼提问 id 幂等。 */
    @PostMapping("/question-answer/{id}")
    public ApiResponse<ReportCommentResponse> upsertQuestionAnswer(
            @PathVariable String id,
            @Valid @RequestBody UpsertQuestionAnswerRequest request,
            HttpServletRequest httpRequest) {
        ViewerAuthInterceptor.requireViewerUsername(httpRequest);
        return ApiResponse.ok(watchCommentService.upsertQuestionAnswer(id, request));
    }

    @PostMapping("/delete/{id}")
    public ApiResponse<ReportCommentResponse> delete(
            @PathVariable String id,
            @Valid @RequestBody DeleteCommentRequest request,
            HttpServletRequest httpRequest) {
        ViewerAuthInterceptor.requireViewerUsername(httpRequest);
        return ApiResponse.ok(watchCommentService.softDelete(id, request));
    }
}
