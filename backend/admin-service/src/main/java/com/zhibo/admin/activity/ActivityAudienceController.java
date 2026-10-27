package com.zhibo.admin.activity;

import com.zhibo.admin.activity.dto.AudienceCommentListResponse;
import com.zhibo.admin.activity.dto.AudienceImportResponse;
import com.zhibo.admin.activity.dto.AudienceListResponse;
import com.zhibo.admin.activity.dto.CreateAudienceRequest;
import com.zhibo.admin.activity.dto.CreateAudienceResponse;
import com.zhibo.admin.activity.dto.UpdateEntitlementRequest;
import com.zhibo.admin.activity.dto.UpdateEntitlementResponse;
import com.zhibo.admin.auth.AuthException;
import com.zhibo.admin.auth.AuthInterceptor;
import com.zhibo.admin.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 用户运营 · 用户管理（D77）。 */
@RestController
@RequestMapping("/api/v1/activities/audiences")
@RequiredArgsConstructor
public class ActivityAudienceController {

    private final ActivityAudienceService audienceService;

    @GetMapping("/list/{id}")
    public ApiResponse<AudienceListResponse> list(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keywordType,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tagIds,
            @RequestParam(required = false) String tagMatch,
            @RequestParam(required = false) Integer entitlementStatus,
            @RequestParam(required = false) String totalWatchDurationOp,
            @RequestParam(required = false) Long totalWatchDurationSec,
            @RequestParam(required = false) String liveWatchDurationOp,
            @RequestParam(required = false) Long liveWatchDurationSec,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String order) {
        return ApiResponse.ok(audienceService.list(
                id,
                page,
                pageSize,
                keywordType,
                keyword,
                parseTagIdsParam(tagIds),
                tagMatch,
                entitlementStatus,
                totalWatchDurationOp,
                totalWatchDurationSec,
                liveWatchDurationOp,
                liveWatchDurationSec,
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
                long tagId = Long.parseLong(p);
                if (tagId > 0) {
                    out.add(tagId);
                }
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return out;
    }

    @PostMapping("/create/{id}")
    public ApiResponse<CreateAudienceResponse> create(
            @PathVariable String id,
            @Valid @RequestBody CreateAudienceRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(audienceService.create(id, request.getPhone(), currentUserId(httpRequest)));
    }

    @PostMapping(value = "/import/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AudienceImportResponse> importFile(
            @PathVariable String id,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(audienceService.importCsv(id, file, currentUserId(httpRequest)));
    }

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> importTemplate() {
        byte[] body = audienceService.importTemplateCsv();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"audience-import.csv\"")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(body);
    }

    @PutMapping("/entitlement/{rowId}")
    public ApiResponse<UpdateEntitlementResponse> entitlement(
            @PathVariable long rowId, @Valid @RequestBody UpdateEntitlementRequest request) {
        return ApiResponse.ok(audienceService.updateEntitlement(rowId, request.getEntitlementStatus()));
    }

    @GetMapping("/comments/{id}")
    public ApiResponse<AudienceCommentListResponse> comments(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String nickname) {
        return ApiResponse.ok(audienceService.listComments(id, page, pageSize, nickname));
    }

    private static long currentUserId(HttpServletRequest request) {
        Object raw = request.getAttribute(AuthInterceptor.ATTR_USER_ID);
        if (raw instanceof Long id) {
            return id;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        throw new AuthException(401, "未登录");
    }
}
