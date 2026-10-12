package com.zhibo.admin.material;

import com.zhibo.admin.material.dto.CreateMaterialGroupRequest;
import com.zhibo.admin.material.dto.DeleteIdResponse;
import com.zhibo.admin.material.dto.MaterialGroupResponse;
import com.zhibo.admin.material.dto.MaterialListResponse;
import com.zhibo.admin.material.dto.MaterialItemResponse;
import com.zhibo.admin.material.dto.RegisterMaterialsRequest;
import com.zhibo.admin.material.dto.RegisterMaterialsResponse;
import com.zhibo.admin.material.dto.RenameMaterialGroupRequest;
import com.zhibo.admin.material.dto.RenameMaterialRequest;
import com.zhibo.admin.web.ApiResponse;
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
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(materialService.list(type, groupId, keyword, page, pageSize));
    }

    @PostMapping
    public ApiResponse<RegisterMaterialsResponse> register(
            @RequestBody RegisterMaterialsRequest request) {
        return ApiResponse.ok(materialService.register(request));
    }

    /** 只改展示名；不改 TOS objectKey/url。 */
    @PutMapping("/{id}")
    public ApiResponse<MaterialItemResponse> rename(
            @PathVariable String id,
            @RequestBody RenameMaterialRequest request) {
        return ApiResponse.ok(materialService.rename(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteIdResponse> softDelete(@PathVariable String id) {
        return ApiResponse.ok(materialService.softDelete(id));
    }
}
