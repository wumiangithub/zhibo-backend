package com.zhibo.live.watch;

import com.zhibo.live.viewer.ViewerAuthException;
import com.zhibo.live.viewer.ViewerAuthInterceptor;
import com.zhibo.live.watch.dto.EnrollCourseRequest;
import com.zhibo.live.watch.dto.EnrollCourseResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 课程分享链 enroll；观众 id 只从 JWT 取。 */
@RestController
@RequestMapping("/api/v1/watch/course")
@RequiredArgsConstructor
public class CourseEnrollController {

    private final CourseEnrollService courseEnrollService;

    @PostMapping("/enroll/{id}")
    public ApiResponse<EnrollCourseResponse> enroll(
            @PathVariable String id,
            @RequestBody(required = false) EnrollCourseRequest request,
            HttpServletRequest httpRequest) {
        String viewerUsername = (String) httpRequest.getAttribute(ViewerAuthInterceptor.ATTR_VIEWER_USERNAME);
        if (viewerUsername == null || viewerUsername.isBlank()) {
            throw new ViewerAuthException(401, "未登录");
        }
        return ApiResponse.ok(
                courseEnrollService.enroll(
                        id, viewerUsername, request == null ? new EnrollCourseRequest() : request));
    }
}
