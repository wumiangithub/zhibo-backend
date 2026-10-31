package com.zhibo.admin.course;

import com.zhibo.admin.auth.AuthException;
import com.zhibo.admin.auth.AuthInterceptor;
import com.zhibo.admin.course.dto.BindLivesRequest;
import com.zhibo.admin.course.dto.BindLivesResponse;
import com.zhibo.admin.course.dto.CatalogListResponse;
import com.zhibo.admin.course.dto.CatalogNodeView;
import com.zhibo.admin.course.dto.CatalogUsedContentsResponse;
import com.zhibo.admin.course.dto.CourseAuditListResponse;
import com.zhibo.admin.course.dto.CourseAuditRequest;
import com.zhibo.admin.course.dto.CourseAuditResultResponse;
import com.zhibo.admin.course.dto.CourseDetailResponse;
import com.zhibo.admin.course.dto.CourseIdResponse;
import com.zhibo.admin.course.dto.CourseListResponse;
import com.zhibo.admin.course.dto.CourseShelfRequest;
import com.zhibo.admin.course.dto.CourseShelfResponse;
import com.zhibo.admin.course.dto.CreateChapterRequest;
import com.zhibo.admin.course.dto.CreateCourseRequest;
import com.zhibo.admin.course.dto.CatalogNodeIdResponse;
import com.zhibo.admin.course.dto.CreateSubcourseRequest;
import com.zhibo.admin.course.dto.DeleteChapterRequest;
import com.zhibo.admin.course.dto.DeleteSubcourseRequest;
import com.zhibo.admin.course.dto.ReorderCatalogRequest;
import com.zhibo.admin.course.dto.ReorderChaptersRequest;
import com.zhibo.admin.course.dto.ReorderLessonsRequest;
import com.zhibo.admin.course.dto.ReorderSubcoursesRequest;
import com.zhibo.admin.course.dto.UpdateChapterRequest;
import com.zhibo.admin.course.dto.UpdateSubcourseRequest;
import com.zhibo.admin.course.dto.UpdateCourseRequest;
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
    private final CourseCatalogService courseCatalogService;

    @PostMapping("/create")
    public ApiResponse<CourseIdResponse> create(
            @Valid @RequestBody CreateCourseRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(courseService.create(request, currentUserId(httpRequest)));
    }

    @PostMapping("/update/{id}")
    public ApiResponse<CourseIdResponse> update(
            @PathVariable String id,
            @Valid @RequestBody UpdateCourseRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(courseService.update(id, request, currentUserId(httpRequest)));
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

    @GetMapping("/catalog/list/{courseId}")
    public ApiResponse<CatalogListResponse> catalogList(@PathVariable String courseId) {
        return ApiResponse.ok(courseCatalogService.list(courseId));
    }

    @GetMapping("/catalog/used-contents/{courseId}")
    public ApiResponse<CatalogUsedContentsResponse> catalogUsedContents(@PathVariable String courseId) {
        return ApiResponse.ok(courseCatalogService.usedContents(courseId));
    }

    @PostMapping("/catalog/lessons/bind-lives/{courseId}")
    public ApiResponse<BindLivesResponse> bindLives(
            @PathVariable String courseId,
            @Valid @RequestBody BindLivesRequest request) {
        return ApiResponse.ok(courseCatalogService.bindLives(courseId, request));
    }

    @PostMapping("/catalog/chapters/create/{courseId}")
    public ApiResponse<CatalogNodeView> createChapter(
            @PathVariable String courseId,
            @Valid @RequestBody CreateChapterRequest request) {
        return ApiResponse.ok(courseCatalogService.createChapter(courseId, request));
    }

    @PostMapping("/catalog/chapters/update/{courseId}")
    public ApiResponse<CatalogNodeView> updateChapter(
            @PathVariable String courseId,
            @Valid @RequestBody UpdateChapterRequest request) {
        return ApiResponse.ok(courseCatalogService.updateChapter(courseId, request));
    }

    @PostMapping("/catalog/chapters/delete/{courseId}")
    public ApiResponse<CatalogNodeIdResponse> deleteChapter(
            @PathVariable String courseId,
            @Valid @RequestBody DeleteChapterRequest request) {
        return ApiResponse.ok(courseCatalogService.deleteChapter(courseId, request));
    }

    @PostMapping("/catalog/subcourses/create/{courseId}")
    public ApiResponse<CatalogNodeView> createSubcourse(
            @PathVariable String courseId,
            @Valid @RequestBody CreateSubcourseRequest request) {
        return ApiResponse.ok(courseCatalogService.createSubcourse(courseId, request));
    }

    @PostMapping("/catalog/subcourses/update/{courseId}")
    public ApiResponse<CatalogNodeView> updateSubcourse(
            @PathVariable String courseId,
            @Valid @RequestBody UpdateSubcourseRequest request) {
        return ApiResponse.ok(courseCatalogService.updateSubcourse(courseId, request));
    }

    @PostMapping("/catalog/subcourses/delete/{courseId}")
    public ApiResponse<CatalogNodeIdResponse> deleteSubcourse(
            @PathVariable String courseId,
            @Valid @RequestBody DeleteSubcourseRequest request) {
        return ApiResponse.ok(courseCatalogService.deleteSubcourse(courseId, request));
    }

    @PostMapping("/catalog/subcourses/reorder/{courseId}")
    public ApiResponse<Void> reorderSubcourses(
            @PathVariable String courseId,
            @Valid @RequestBody ReorderSubcoursesRequest request) {
        courseCatalogService.reorderSubcourses(courseId, request);
        return ApiResponse.ok();
    }

    @PostMapping("/catalog/reorder/{courseId}")
    public ApiResponse<Void> reorderCatalog(
            @PathVariable String courseId,
            @Valid @RequestBody ReorderCatalogRequest request) {
        courseCatalogService.reorderCatalog(courseId, request);
        return ApiResponse.ok();
    }

    @PostMapping("/catalog/chapters/reorder/{courseId}")
    public ApiResponse<Void> reorderChapters(
            @PathVariable String courseId,
            @Valid @RequestBody ReorderChaptersRequest request) {
        courseCatalogService.reorderChapters(courseId, request);
        return ApiResponse.ok();
    }

    @PostMapping("/catalog/lessons/reorder/{courseId}")
    public ApiResponse<Void> reorderLessons(
            @PathVariable String courseId,
            @Valid @RequestBody ReorderLessonsRequest request) {
        courseCatalogService.reorderLessons(courseId, request);
        return ApiResponse.ok();
    }

    private static long currentUserId(HttpServletRequest request) {
        Object raw = request.getAttribute(AuthInterceptor.ATTR_USER_ID);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        throw new AuthException(401, "未登录");
    }
}
