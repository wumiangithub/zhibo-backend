package com.zhibo.live.watch;

import com.zhibo.live.watch.dto.DeleteCommentRequest;
import com.zhibo.live.watch.dto.ReportCommentRequest;
import com.zhibo.live.watch.dto.ReportCommentResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/watch/comments")
@RequiredArgsConstructor
public class WatchCommentController {

    private final WatchCommentService watchCommentService;

    @PostMapping("/report/{id}")
    public ApiResponse<ReportCommentResponse> report(
            @PathVariable String id, @Valid @RequestBody ReportCommentRequest request) {
        return ApiResponse.ok(watchCommentService.report(id, request));
    }

    @PostMapping("/delete/{id}")
    public ApiResponse<ReportCommentResponse> delete(
            @PathVariable String id, @Valid @RequestBody DeleteCommentRequest request) {
        return ApiResponse.ok(watchCommentService.softDelete(id, request));
    }
}
