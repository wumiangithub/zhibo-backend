package com.zhibo.admin.course;

import com.zhibo.admin.auth.AuthException;
import com.zhibo.admin.auth.AuthInterceptor;
import com.zhibo.admin.course.dto.CourseAuditListResponse;
import com.zhibo.admin.course.dto.CourseAuditRequest;
import com.zhibo.admin.course.dto.CourseAuditResultResponse;
import com.zhibo.admin.course.dto.CourseDetailResponse;
import com.zhibo.admin.course.dto.CourseIdResponse;
import com.zhibo.admin.course.dto.CourseListResponse;
import com.zhibo.admin.course.dto.CourseShelfRequest;
import com.zhibo.admin.course.dto.CourseShelfResponse;
import com.zhibo.admin.course.dto.CreateCourseRequest;
import com.zhibo.admin.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 课程管理。路径命名 D62：动作在前、单 id 末尾。 */
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @PostMapping("/create")
    public ApiResponse<CourseIdResponse> create(
            @Valid @RequestBody CreateCourseRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(courseService.create(request, currentUserId(httpRequest)));
    }

    @GetMapping("/list")
    public ApiResponse<CourseListResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String shelfStatus,
            @RequestParam(required = false) String ownerName,
            @RequestParam(required = false) String riskStatus,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order) {
        return ApiResponse.ok(courseService.list(
                page, pageSize, keyword, shelfStatus, ownerName, riskStatus, sort, order));
    }

    @GetMapping("/detail/{id}")
    public ApiResponse<CourseDetailResponse> detail(@PathVariable String id) {
        return ApiResponse.ok(courseService.detail(id));
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<CourseIdResponse> delete(@PathVariable String id) {
        return ApiResponse.ok(courseService.delete(id));
    }

    @PostMapping("/shelf/{id}")
    public ApiResponse<CourseShelfResponse> shelf(
            @PathVariable String id,
            @Valid @RequestBody CourseShelfRequest request) {
        return ApiResponse.ok(courseService.shelf(id, request));
    }

    @GetMapping("/audits/{id}")
    public ApiResponse<CourseAuditListResponse> audits(@PathVariable String id) {
        return ApiResponse.ok(courseService.audits(id));
    }

    @PostMapping("/audit/{id}")
    public ApiResponse<CourseAuditResultResponse> audit(
            @PathVariable String id,
            @Valid @RequestBody CourseAuditRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(courseService.audit(id, request, currentUserId(httpRequest)));
    }

    private static long currentUserId(HttpServletRequest request) {
        Object raw = request.getAttribute(AuthInterceptor.ATTR_USER_ID);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        throw new AuthException(401, "未登录");
    }
}
