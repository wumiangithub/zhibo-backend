package com.zhibo.admin.viewertag;

import com.zhibo.admin.auth.AuthException;
import com.zhibo.admin.auth.AuthInterceptor;
import com.zhibo.admin.viewertag.dto.CreateViewerTagGroupRequest;
import com.zhibo.admin.viewertag.dto.CreateViewerTagRequest;
import com.zhibo.admin.viewertag.dto.DeleteViewerTagGroupResponse;
import com.zhibo.admin.viewertag.dto.DeleteViewerTagResponse;
import com.zhibo.admin.viewertag.dto.UpdateViewerTagGroupRequest;
import com.zhibo.admin.viewertag.dto.UpdateViewerTagRequest;
import com.zhibo.admin.viewertag.dto.ViewerTagGroupItem;
import com.zhibo.admin.viewertag.dto.ViewerTagGroupPageItem;
import com.zhibo.admin.viewertag.dto.ViewerTagGroupPageResponse;
import com.zhibo.admin.viewertag.dto.ViewerTagItem;
import com.zhibo.admin.viewertag.dto.ViewerTagListResponse;
import com.zhibo.admin.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
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

/** 顶栏用户管理 · 标签 / 分组（D80 / D81）。 */
@RestController
@RequestMapping("/api/v1/viewer-tags")
@RequiredArgsConstructor
public class ViewerTagAdminController {

    private final ViewerTagAdminService viewerTagAdminService;

    @GetMapping("/groups/list")
    public ApiResponse<List<ViewerTagGroupItem>> listGroups() {
        return ApiResponse.ok(viewerTagAdminService.listGroups());
    }

    @GetMapping("/groups/page")
    public ApiResponse<ViewerTagGroupPageResponse> pageGroups(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String name) {
        return ApiResponse.ok(viewerTagAdminService.pageGroups(page, pageSize, name));
    }

    @PostMapping("/groups/create")
    public ApiResponse<ViewerTagGroupPageItem> createGroup(
            @RequestBody CreateViewerTagGroupRequest request) {
        return ApiResponse.ok(viewerTagAdminService.createGroup(request));
    }

    @PutMapping("/groups/update/{id}")
    public ApiResponse<ViewerTagGroupPageItem> updateGroup(
            @PathVariable long id, @RequestBody UpdateViewerTagGroupRequest request) {
        return ApiResponse.ok(viewerTagAdminService.updateGroup(id, request));
    }

    @DeleteMapping("/groups/delete/{id}")
    public ApiResponse<DeleteViewerTagGroupResponse> deleteGroup(@PathVariable long id) {
        return ApiResponse.ok(viewerTagAdminService.deleteGroup(id));
    }

    @GetMapping("/list")
    public ApiResponse<ViewerTagListResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) Integer type,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Long groupId) {
        return ApiResponse.ok(viewerTagAdminService.list(page, pageSize, type, name, groupId));
    }

    @GetMapping("/detail/{id}")
    public ApiResponse<ViewerTagItem> detail(@PathVariable long id) {
        return ApiResponse.ok(viewerTagAdminService.detail(id));
    }

    @PostMapping("/create")
    public ApiResponse<ViewerTagItem> create(@RequestBody CreateViewerTagRequest request) {
        return ApiResponse.ok(viewerTagAdminService.create(request));
    }

    @PutMapping("/update/{id}")
    public ApiResponse<ViewerTagItem> update(
            @PathVariable long id, @RequestBody UpdateViewerTagRequest request) {
        return ApiResponse.ok(viewerTagAdminService.update(id, request));
    }

    @DeleteMapping("/delete/{id}")
    public ApiResponse<DeleteViewerTagResponse> delete(
            @PathVariable long id, HttpServletRequest httpRequest) {
        return ApiResponse.ok(viewerTagAdminService.delete(id, currentUserId(httpRequest)));
    }

    private static long currentUserId(HttpServletRequest request) {
        Object raw = request.getAttribute(AuthInterceptor.ATTR_USER_ID);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        throw new AuthException(401, "未登录");
    }
}
