package com.zhibo.live.watch;

import com.zhibo.live.viewer.ViewerAuthException;
import com.zhibo.live.viewer.ViewerAuthInterceptor;
import com.zhibo.live.watch.dto.CourseLessonProgressRequest;
import com.zhibo.live.watch.dto.CourseLessonProgressResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 课程小节学习进度上报（D101）。 */
@RestController
@RequestMapping("/api/v1/watch/course/lessons")
@RequiredArgsConstructor
public class CourseLessonProgressController {

    private final CourseLessonProgressService courseLessonProgressService;

    @PostMapping("/progress/{courseId}")
    public ApiResponse<CourseLessonProgressResponse> progress(
            @PathVariable String courseId,
            @RequestBody CourseLessonProgressRequest request,
            HttpServletRequest httpRequest) {
        Long viewerId = (Long) httpRequest.getAttribute(ViewerAuthInterceptor.ATTR_VIEWER_ID);
        if (viewerId == null || viewerId <= 0) {
            throw new ViewerAuthException(401, "未登录");
        }
        return ApiResponse.ok(courseLessonProgressService.report(courseId, viewerId, request));
    }
}
