package com.zhibo.admin.course;

import com.zhibo.admin.course.dto.OutsideCourseListResponse;
import com.zhibo.admin.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 第三方课程列表。当前不鉴权，管理端登录拦截器已放行本路径。 */
@RestController
@RequestMapping("/api/v1/outside/courses")
@RequiredArgsConstructor
public class OutsideCourseController {

    private final CourseService courseService;

    @GetMapping("/list")
    public ApiResponse<OutsideCourseListResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(courseService.outsideList(page, pageSize, keyword));
    }
}
