package com.zhibo.admin.activity;

import com.zhibo.admin.activity.dto.ActivityDetailResponse;
import com.zhibo.admin.activity.dto.ActivityEmbedResponse;
import com.zhibo.admin.activity.dto.ActivityHostResponse;
import com.zhibo.admin.activity.dto.ActivityListResponse;
import com.zhibo.admin.activity.dto.ActivityRecordListResponse;
import com.zhibo.admin.activity.dto.AuditListResponse;
import com.zhibo.admin.activity.dto.AuditRequest;
import com.zhibo.admin.activity.dto.AuditResultResponse;
import com.zhibo.admin.activity.dto.CreateActivityRequest;
import com.zhibo.admin.activity.dto.CreateActivityResponse;
import com.zhibo.admin.activity.dto.DeleteActivityResponse;
import com.zhibo.admin.activity.dto.EndActivityResponse;
import com.zhibo.admin.activity.dto.PlaybackUpdateRequest;
import com.zhibo.admin.activity.dto.SalesUpdateRequest;
import com.zhibo.admin.activity.dto.SalesUpdateResponse;
import com.zhibo.admin.activity.dto.ShelfRequest;
import com.zhibo.admin.activity.dto.ShelfResponse;
import com.zhibo.admin.activity.dto.UpdateActivityRequest;
import com.zhibo.admin.auth.AuthException;
import com.zhibo.admin.auth.AuthInterceptor;
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

@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;
    private final HostService hostService;
    private final EmbedService embedService;

    @PostMapping
    public ApiResponse<CreateActivityResponse> create(@Valid @RequestBody CreateActivityRequest request) {
        return ApiResponse.ok(activityService.create(request));
    }

    @GetMapping
    public ApiResponse<ActivityListResponse> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer state,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String shelfStatus,
            @RequestParam(required = false) String startFrom,
            @RequestParam(required = false) String startTo) {
        return ApiResponse.ok(activityService.list(
                page, pageSize, keyword, state, provider, shelfStatus, startFrom, startTo));
    }

    @GetMapping("/{id}")
    public ApiResponse<ActivityDetailResponse> detail(@PathVariable String id) {
        return ApiResponse.ok(activityService.detail(id));
    }

    /** 某场活动的回放列表；list[].id = create-demand 的 record_id。 */
    @GetMapping("/{id}/records")
    public ApiResponse<ActivityRecordListResponse> records(@PathVariable String id) {
        return ApiResponse.ok(activityService.listRecords(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<CreateActivityResponse> update(
            @PathVariable String id,
            @RequestBody UpdateActivityRequest request) {
        return ApiResponse.ok(activityService.update(id, request));
    }

    @PutMapping("/{id}/playback")
    public ApiResponse<CreateActivityResponse> updatePlayback(
            @PathVariable String id,
            @Valid @RequestBody PlaybackUpdateRequest request) {
        return ApiResponse.ok(activityService.updatePlayback(id, request));
    }

    @PutMapping("/{id}/sales")
    public ApiResponse<SalesUpdateResponse> updateSales(
            @PathVariable String id,
            @Valid @RequestBody SalesUpdateRequest request) {
        return ApiResponse.ok(activityService.updateSales(id, request));
    }

    @PostMapping("/{id}/end")
    public ApiResponse<EndActivityResponse> end(@PathVariable String id) {
        return ApiResponse.ok(activityService.end(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteActivityResponse> delete(@PathVariable String id) {
        return ApiResponse.ok(activityService.delete(id));
    }

    @PostMapping("/{id}/shelf")
    public ApiResponse<ShelfResponse> shelf(@PathVariable String id, @Valid @RequestBody ShelfRequest request) {
        return ApiResponse.ok(activityService.shelf(id, request));
    }

    @GetMapping("/{id}/audits")
    public ApiResponse<AuditListResponse> audits(@PathVariable String id) {
        return ApiResponse.ok(activityService.audits(id));
    }

    @PostMapping("/{id}/audit")
    public ApiResponse<AuditResultResponse> audit(
            @PathVariable String id,
            @Valid @RequestBody AuditRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(activityService.audit(id, request, currentUserId(httpRequest)));
    }

    private static long currentUserId(HttpServletRequest request) {
        Object raw = request.getAttribute(AuthInterceptor.ATTR_USER_ID);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        throw new AuthException(401, "未登录");
    }

    @GetMapping("/{id}/host")
    public ApiResponse<ActivityHostResponse> host(@PathVariable String id) {
        return ApiResponse.ok(hostService.host(id));
    }

    @GetMapping("/{id}/embed")
    public ApiResponse<ActivityEmbedResponse> embed(@PathVariable String id) {
        return ApiResponse.ok(embedService.embed(id));
    }
}
