package com.zhibo.admin.material;

import com.zhibo.admin.material.dto.ArticleDetailResponse;
import com.zhibo.admin.material.dto.CreateArticleRequest;
import com.zhibo.admin.material.dto.CreateMaterialGroupRequest;
import com.zhibo.admin.material.dto.DeleteIdResponse;
import com.zhibo.admin.material.dto.MaterialGroupResponse;
import com.zhibo.admin.material.dto.MaterialListResponse;
import com.zhibo.admin.material.dto.MaterialItemResponse;
import com.zhibo.admin.material.dto.MoveMaterialRequest;
import com.zhibo.admin.material.dto.RegisterMaterialsRequest;
import com.zhibo.admin.material.dto.RegisterMaterialsResponse;
import com.zhibo.admin.material.dto.RenameMaterialGroupRequest;
import com.zhibo.admin.material.dto.RenameMaterialRequest;
import com.zhibo.admin.material.dto.ReplaceImageRequest;
import com.zhibo.admin.material.dto.UpdateMaterialAuditRequest;
import com.zhibo.admin.auth.AuthException;
import com.zhibo.admin.auth.AuthInterceptor;
import com.zhibo.admin.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.List;

/**
 * 素材中心：分组 + 元数据登记（文件已在 TOS）。
 */
@RestController
@RequestMapping("/api/materials")
@RequiredArgsConstructor
public class MaterialController {

    private final MaterialService materialService;

    @GetMapping("/groups")
    public ApiResponse<List<MaterialGroupResponse>> listGroups(@RequestParam String type) {
        return ApiResponse.ok(materialService.listGroups(type));
    }

    @PostMapping("/groups")
    public ApiResponse<MaterialGroupResponse> createGroup(
            @RequestBody CreateMaterialGroupRequest request) {
        return ApiResponse.ok(materialService.createGroup(request));
    }

    @PutMapping("/groups/{id}")
    public ApiResponse<MaterialGroupResponse> renameGroup(
            @PathVariable String id,
            @RequestBody RenameMaterialGroupRequest request) {
        return ApiResponse.ok(materialService.renameGroup(id, request));
    }

    @DeleteMapping("/groups/{id}")
    public ApiResponse<DeleteIdResponse> deleteGroup(@PathVariable String id) {
        return ApiResponse.ok(materialService.deleteGroup(id));
    }

    @GetMapping
    public ApiResponse<MaterialListResponse> list(
            @RequestParam String type,
            @RequestParam(required = false) String groupId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "false") boolean selectableOnly,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(
                materialService.list(type, groupId, keyword, selectableOnly, page, pageSize));
    }

    /** 文章详情，含正文。 */
    @GetMapping("/articles/{id}")
    public ApiResponse<ArticleDetailResponse> getArticle(@PathVariable String id) {
        return ApiResponse.ok(materialService.getArticle(id));
    }

    /** 编辑文章。待审核返回 400。成功后两审回到 pending。 */
    @PutMapping("/articles/{id}")
    public ApiResponse<MaterialItemResponse> updateArticle(
            @PathVariable String id,
            @RequestBody CreateArticleRequest request) {
        return ApiResponse.ok(materialService.updateArticle(id, request));
    }

    /** 新建文章：标题 / 作者 / HTML，不经 TOS 登记。 */
    @PostMapping("/articles")
    public ApiResponse<MaterialItemResponse> createArticle(
            @RequestBody CreateArticleRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(materialService.createArticle(request, currentUserId(httpRequest)));
    }

    @PostMapping
    public ApiResponse<RegisterMaterialsResponse> register(
            @RequestBody RegisterMaterialsRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(materialService.register(request, currentUserId(httpRequest)));
    }

    private static long currentUserId(HttpServletRequest request) {
        Object raw = request.getAttribute(AuthInterceptor.ATTR_USER_ID);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        throw new AuthException(401, "未登录");
    }

    /** 移到同类型其他分组。不改审核状态。 */
    @PutMapping("/{id}/group")
    public ApiResponse<MaterialItemResponse> move(
            @PathVariable String id,
            @RequestBody MoveMaterialRequest request) {
        return ApiResponse.ok(materialService.move(id, request));
    }

    /** 裁剪后替换图片。待审核返回 400。成功后两审回到 pending。 */
    @PutMapping("/{id}/image")
    public ApiResponse<MaterialItemResponse> replaceImage(
            @PathVariable String id,
            @RequestBody ReplaceImageRequest request) {
        return ApiResponse.ok(materialService.replaceImage(id, request));
    }

    /** 只改展示名；不改 TOS objectKey/url。 */
    @PutMapping("/{id}")
    public ApiResponse<MaterialItemResponse> rename(
            @PathVariable String id,
            @RequestBody RenameMaterialRequest request) {
        return ApiResponse.ok(materialService.rename(id, request));
    }

    /** AI 审核：仅 passed / rejected。 */
    @PutMapping("/{id}/ai-audit")
    public ApiResponse<MaterialItemResponse> updateAiAudit(
            @PathVariable String id,
            @RequestBody UpdateMaterialAuditRequest request) {
        return ApiResponse.ok(materialService.updateAiAudit(id, request));
    }

    /** 风控合规：仅 passed / rejected。 */
    @PutMapping("/{id}/risk-audit")
    public ApiResponse<MaterialItemResponse> updateRiskAudit(
            @PathVariable String id,
            @RequestBody UpdateMaterialAuditRequest request) {
        return ApiResponse.ok(materialService.updateRiskAudit(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteIdResponse> softDelete(@PathVariable String id) {
        return ApiResponse.ok(materialService.softDelete(id));
    }
}
