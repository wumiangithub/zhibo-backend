package com.zhibo.admin.course;

import com.zhibo.admin.auth.AuthException;
import com.zhibo.admin.auth.AuthInterceptor;
import com.zhibo.admin.course.dto.BindLivesRequest;
import com.zhibo.admin.course.dto.BindLivesResponse;
import com.zhibo.admin.course.dto.BindMaterialsRequest;
import com.zhibo.admin.course.dto.BindMaterialsResponse;
import com.zhibo.admin.course.dto.CatalogListResponse;
import com.zhibo.admin.course.dto.CatalogNodeView;
import com.zhibo.admin.course.dto.CatalogUsedContentsResponse;
import com.zhibo.admin.course.dto.CourseAnalyticsLessonListResponse;
import com.zhibo.admin.course.dto.CourseAnalyticsUserLessonListResponse;
import com.zhibo.admin.course.dto.CourseAnalyticsUserListResponse;
import com.zhibo.admin.course.dto.CourseAuditListResponse;
import com.zhibo.admin.course.dto.CourseAuditRequest;
import com.zhibo.admin.course.dto.CourseAuditResultResponse;
import com.zhibo.admin.course.dto.CourseDetailResponse;
import com.zhibo.admin.course.dto.CourseIdResponse;
import com.zhibo.admin.course.dto.CourseListResponse;
import com.zhibo.admin.course.dto.CourseSalesUpdateRequest;
import com.zhibo.admin.course.dto.CourseSalesUpdateResponse;
import com.zhibo.admin.course.dto.CourseShelfRequest;
import com.zhibo.admin.course.dto.CourseShelfResponse;
import com.zhibo.admin.course.dto.CourseUserEntitlementRequest;
import com.zhibo.admin.course.dto.CourseUserEntitlementResponse;
import com.zhibo.admin.course.dto.CourseUserExpireRequest;
import com.zhibo.admin.course.dto.CourseUserExpireResponse;
import com.zhibo.admin.course.dto.CourseUserImportHistoryListResponse;
import com.zhibo.admin.course.dto.CourseUserImportResponse;
import com.zhibo.admin.course.dto.CourseUserListResponse;
import com.zhibo.admin.course.dto.CreateCourseUserRequest;
import com.zhibo.admin.course.dto.CreateCourseUserResponse;
import com.zhibo.admin.course.dto.ImportCourseUsersByTagsEstimateResponse;
import com.zhibo.admin.course.dto.ImportCourseUsersByTagsRequest;
import com.zhibo.admin.course.dto.ImportCourseUsersByTagsResponse;
import com.zhibo.admin.course.dto.CreateChapterRequest;
import com.zhibo.admin.course.dto.CreateCourseRequest;
import com.zhibo.admin.course.dto.CatalogNodeIdResponse;
import com.zhibo.admin.course.dto.CreateSubcourseRequest;
import com.zhibo.admin.course.dto.DeleteChapterRequest;
import com.zhibo.admin.course.dto.DeleteSubcourseRequest;
import com.zhibo.admin.course.dto.ReorderCatalogRequest;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** 课程管理。路径命名 D62：动作在前、单 id 末尾。 */
@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;
    private final CourseCatalogService courseCatalogService;
    private final CourseUserService courseUserService;
    private final CourseAnalyticsService courseAnalyticsService;

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

    @PutMapping("/sales/{id}")
    public ApiResponse<CourseSalesUpdateResponse> updateSales(
            @PathVariable String id,
            @Valid @RequestBody CourseSalesUpdateRequest request) {
        return ApiResponse.ok(courseService.updateSales(id, request));
    }

    @GetMapping("/users/list/{courseId}")
    public ApiResponse<CourseUserListResponse> listUsers(
            @PathVariable String courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keywordType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String entitlementState) {
        return ApiResponse.ok(courseUserService.list(
                courseId, page, pageSize, keywordType, keyword, entitlementState));
    }

    @PutMapping("/users/entitlement/{rowId}")
    public ApiResponse<CourseUserEntitlementResponse> updateUserEntitlement(
            @PathVariable long rowId,
            @Valid @RequestBody CourseUserEntitlementRequest request) {
        return ApiResponse.ok(
                courseUserService.updateEntitlement(rowId, request.getEntitlementStatus()));
    }

    @PutMapping("/users/expire/{rowId}")
    public ApiResponse<CourseUserExpireResponse> updateUserExpire(
            @PathVariable long rowId,
            @RequestBody CourseUserExpireRequest request) {
        return ApiResponse.ok(courseUserService.updateExpire(rowId, request));
    }

    @PostMapping("/users/create/{courseId}")
    public ApiResponse<CreateCourseUserResponse> createUser(
            @PathVariable String courseId,
            @RequestBody CreateCourseUserRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                courseUserService.create(courseId, request, currentUserId(httpRequest)));
    }

    @PostMapping("/users/import/{courseId}")
    public ApiResponse<CourseUserImportResponse> importUsers(
            @PathVariable String courseId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "expireTime", required = false) String expireTime,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                courseUserService.importCsv(courseId, file, expireTime, currentUserId(httpRequest)));
    }

    @PostMapping("/users/import-by-tags/estimate/{courseId}")
    public ApiResponse<ImportCourseUsersByTagsEstimateResponse> estimateUsersByTags(
            @PathVariable String courseId, @RequestBody ImportCourseUsersByTagsRequest request) {
        return ApiResponse.ok(courseUserService.estimateByTags(courseId, request));
    }

    @PostMapping("/users/import-by-tags/{courseId}")
    public ApiResponse<ImportCourseUsersByTagsResponse> importUsersByTags(
            @PathVariable String courseId,
            @RequestBody ImportCourseUsersByTagsRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(
                courseUserService.importByTags(courseId, request, currentUserId(httpRequest)));
    }

    @GetMapping("/users/import-template")
    public ResponseEntity<byte[]> importTemplate() {
        return csvAttachment("course-user-import.csv", courseUserService.importTemplateCsv());
    }

    @GetMapping("/users/imports/list/{courseId}")
    public ApiResponse<CourseUserImportHistoryListResponse> listUserImports(
            @PathVariable String courseId) {
        return ApiResponse.ok(courseUserService.listImportHistory(courseId));
    }

    @GetMapping("/users/imports/failures/{importId}")
    public ResponseEntity<byte[]> importFailures(@PathVariable long importId) {
        return csvAttachment(
                "course-user-import-failures.csv", courseUserService.importFailuresCsv(importId));
    }

    @GetMapping("/users/export/{courseId}")
    public ResponseEntity<byte[]> exportUsers(
            @PathVariable String courseId,
            @RequestParam(required = false) String keywordType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String entitlementState) {
        return csvAttachment(
                "course-user-export.csv",
                courseUserService.exportCsv(courseId, keywordType, keyword, entitlementState));
    }

    @GetMapping("/analytics/lessons/{courseId}")
    public ApiResponse<CourseAnalyticsLessonListResponse> analyticsLessons(
            @PathVariable String courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return ApiResponse.ok(
                courseAnalyticsService.listLessons(courseId, page, pageSize, sortBy, sortOrder));
    }

    @GetMapping("/analytics/lessons/export/{courseId}")
    public ResponseEntity<byte[]> exportAnalyticsLessons(
            @PathVariable String courseId,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortOrder) {
        return csvAttachment(
                "course-analytics-lessons.csv",
                courseAnalyticsService.exportLessonsCsv(courseId, sortBy, sortOrder));
    }

    @GetMapping("/analytics/users/{courseId}")
    public ApiResponse<CourseAnalyticsUserListResponse> analyticsUsers(
            @PathVariable String courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keywordType,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(
                courseAnalyticsService.listUsers(courseId, page, pageSize, keywordType, keyword));
    }

    @GetMapping("/analytics/users/export/{courseId}")
    public ResponseEntity<byte[]> exportAnalyticsUsers(
            @PathVariable String courseId,
            @RequestParam(required = false) String keywordType,
            @RequestParam(required = false) String keyword) {
        return csvAttachment(
                "course-analytics-users.csv",
                courseAnalyticsService.exportUsersCsv(courseId, keywordType, keyword));
    }

    @GetMapping("/analytics/users/lessons/{courseId}")
    public ApiResponse<CourseAnalyticsUserLessonListResponse> analyticsUserLessons(
            @PathVariable String courseId,
            @RequestParam Long viewerId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.ok(
                courseAnalyticsService.listUserLessons(courseId, viewerId, page, pageSize));
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<CourseIdResponse> delete(@PathVariable String id) {
        return ApiResponse.ok(courseService.delete(id));
    }

    private static ResponseEntity<byte[]> csvAttachment(String filename, byte[] body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(body);
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

    @PostMapping("/catalog/lessons/bind-materials/{courseId}")
    public ApiResponse<BindMaterialsResponse> bindMaterials(
            @PathVariable String courseId,
            @Valid @RequestBody BindMaterialsRequest request) {
        return ApiResponse.ok(courseCatalogService.bindMaterials(courseId, request));
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

    private static long currentUserId(HttpServletRequest request) {
        Object raw = request.getAttribute(AuthInterceptor.ATTR_USER_ID);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        throw new AuthException(401, "未登录");
    }
}
