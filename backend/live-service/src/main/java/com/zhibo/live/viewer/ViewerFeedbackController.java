package com.zhibo.live.viewer;

import com.zhibo.live.viewer.dto.ViewerFeedbackDetailResponse;
import com.zhibo.live.viewer.dto.ViewerFeedbackListResponse;
import com.zhibo.live.viewer.dto.ViewerFeedbackSubmitRequest;
import com.zhibo.live.viewer.dto.ViewerFeedbackSubmitResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 观众意见反馈。需 access。 */
@RestController
@RequestMapping("/api/v1/viewer/feedback")
public class ViewerFeedbackController {

    private final ViewerFeedbackService viewerFeedbackService;

    public ViewerFeedbackController(ViewerFeedbackService viewerFeedbackService) {
        this.viewerFeedbackService = viewerFeedbackService;
    }

    @PostMapping("/submit")
    public ApiResponse<ViewerFeedbackSubmitResponse> submit(
            @RequestBody ViewerFeedbackSubmitRequest request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(viewerFeedbackService.submit(requireViewerId(httpRequest), request));
    }

    @GetMapping("/list")
    public ApiResponse<ViewerFeedbackListResponse> list(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                viewerFeedbackService.list(requireViewerId(httpRequest), page, pageSize));
    }

    @GetMapping("/detail/{id}")
    public ApiResponse<ViewerFeedbackDetailResponse> detail(
            @PathVariable("id") long id, HttpServletRequest httpRequest) {
        return ApiResponse.ok(viewerFeedbackService.detail(requireViewerId(httpRequest), id));
    }

    private static long requireViewerId(HttpServletRequest request) {
        Long viewerId = (Long) request.getAttribute(ViewerAuthInterceptor.ATTR_VIEWER_ID);
        if (viewerId == null) {
            throw new ViewerAuthException(401, "未登录");
        }
        return viewerId;
    }
}
