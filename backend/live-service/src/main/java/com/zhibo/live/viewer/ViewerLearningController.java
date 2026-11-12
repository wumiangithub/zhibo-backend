package com.zhibo.live.viewer;

import com.zhibo.live.viewer.dto.ViewerLearningCourseDetailResponse;
import com.zhibo.live.viewer.dto.ViewerLearningCourseListResponse;
import com.zhibo.live.viewer.dto.ViewerLearningLessonContentResponse;
import com.zhibo.live.watch.dto.WatchActivityListResponse;
import com.zhibo.live.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 小程序「我的学习」。需观众 access。
 * 直播：{@code activity_audience.entitlement_status=1}；课程：{@code course_user} 有效权益。
 */
@RestController
@RequestMapping("/api/v1/viewer/learning")
@RequiredArgsConstructor
public class ViewerLearningController {

    private final ViewerLearningService learningService;

    @GetMapping("/lives")
    public ApiResponse<WatchActivityListResponse> lives(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer state,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                learningService.listEntitledLives(ViewerAuthInterceptor.requireViewerUsername(httpRequest), page, pageSize, state));
    }

    @GetMapping("/courses")
    public ApiResponse<ViewerLearningCourseListResponse> courses(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                learningService.listEntitledCourses(ViewerAuthInterceptor.requireViewerUsername(httpRequest), page, pageSize));
    }

    @GetMapping("/courses/detail/{courseId}")
    public ApiResponse<ViewerLearningCourseDetailResponse> courseDetail(
            @PathVariable String courseId, HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                learningService.getCourseDetail(ViewerAuthInterceptor.requireViewerUsername(httpRequest), courseId));
    }

    @GetMapping("/courses/lessons/{courseId}/{lessonNodeId}")
    public ApiResponse<ViewerLearningLessonContentResponse> lessonContent(
            @PathVariable String courseId,
            @PathVariable long lessonNodeId,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                learningService.getLessonContent(
                        ViewerAuthInterceptor.requireViewerUsername(httpRequest), courseId, lessonNodeId));
    }
}
