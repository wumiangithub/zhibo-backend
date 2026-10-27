package com.zhibo.admin.viewer;

import com.zhibo.admin.viewer.dto.UpdateViewerBlacklistRequest;
import com.zhibo.admin.viewer.dto.UpdateViewerBlacklistResponse;
import com.zhibo.admin.viewer.dto.UpdateViewerStatusRequest;
import com.zhibo.admin.viewer.dto.UpdateViewerStatusResponse;
import com.zhibo.admin.viewer.dto.UpdateViewerTagsRequest;
import com.zhibo.admin.viewer.dto.UpdateViewerTagsResponse;
import com.zhibo.admin.viewer.dto.ViewerListResponse;
import com.zhibo.admin.web.ApiResponse;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 顶栏用户管理 · 用户列表（D79 / D82）。 */
@RestController
@RequestMapping("/api/v1/viewers")
@RequiredArgsConstructor
public class ViewerAdminController {

    private final ViewerAdminService viewerAdminService;

    @GetMapping("/list")
    public ApiResponse<ViewerListResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keywordType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Double consumeAmountMin,
            @RequestParam(required = false) Double consumeAmountMax,
            @RequestParam(required = false) Integer consumeCountMin,
            @RequestParam(required = false) Integer consumeCountMax,
            @RequestParam(required = false) String tagIds,
            @RequestParam(required = false) String tagMatch,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order) {
        return ApiResponse.ok(viewerAdminService.list(
                page,
                pageSize,
                keywordType,
                keyword,
                consumeAmountMin,
                consumeAmountMax,
                consumeCountMin,
                consumeCountMax,
                parseTagIdsParam(tagIds),
                tagMatch,
                sort,
                order));
    }

    /** 支持 `1,2,3` 或单值；非法片段忽略。 */
    static List<Long> parseTagIdsParam(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        List<Long> out = new ArrayList<>();
        for (String part : raw.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            try {
                long id = Long.parseLong(p);
                if (id > 0) {
                    out.add(id);
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return out;
    }

    @PutMapping("/update-status/{id}")
    public ApiResponse<UpdateViewerStatusResponse> updateStatus(
            @PathVariable long id, @Valid @RequestBody UpdateViewerStatusRequest request) {
        return ApiResponse.ok(viewerAdminService.updateStatus(id, request.getStatus()));
    }

    @PutMapping("/update-blacklist/{id}")
    public ApiResponse<UpdateViewerBlacklistResponse> updateBlacklist(
            @PathVariable long id, @Valid @RequestBody UpdateViewerBlacklistRequest request) {
        return ApiResponse.ok(viewerAdminService.updateBlacklist(
                id, request.getBanComment(), request.getBanPurchase(), request.getBanAccess()));
    }

    @PutMapping("/update-tags/{id}")
    public ApiResponse<UpdateViewerTagsResponse> updateTags(
            @PathVariable long id, @Valid @RequestBody UpdateViewerTagsRequest request) {
        return ApiResponse.ok(viewerAdminService.updateTags(id, request.getTagIds()));
    }
}
