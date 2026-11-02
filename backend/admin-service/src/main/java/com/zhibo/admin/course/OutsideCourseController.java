package com.zhibo.admin.course;

import com.zhibo.admin.course.dto.OutsideCourseListRequest;
import com.zhibo.admin.course.dto.OutsideCourseListResponse;
import com.zhibo.admin.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 第三方课程列表。当前不鉴权，管理端登录拦截器已放行本路径。 */
@RestController
@RequestMapping("/api/v1/outside/courses")
@RequiredArgsConstructor
public class OutsideCourseController {

    private final CourseService courseService;

    @PostMapping("/list")
    public ApiResponse<OutsideCourseListResponse> list(
            @RequestBody(required = false) OutsideCourseListRequest request) {
        OutsideCourseListRequest body = request == null ? new OutsideCourseListRequest() : request;
        return ApiResponse.ok(courseService.outsideList(body));
    }
}
