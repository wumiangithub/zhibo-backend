package com.zhibo.admin.activity;

import com.zhibo.admin.activity.dto.ActivityDetailResponse;
import com.zhibo.admin.activity.dto.ActivityEmbedResponse;
import com.zhibo.admin.activity.dto.ActivityHostResponse;
import com.zhibo.admin.activity.dto.ActivityListResponse;
import com.zhibo.admin.activity.dto.ActivityRecordListResponse;
import com.zhibo.admin.activity.dto.CreateActivityRequest;
import com.zhibo.admin.activity.dto.CreateActivityResponse;
import com.zhibo.admin.activity.dto.DeleteActivityResponse;
import com.zhibo.admin.activity.dto.EndActivityResponse;
import com.zhibo.admin.activity.dto.UpdateActivityRequest;
import com.zhibo.admin.web.ApiResponse;
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer state,
            @RequestParam(required = false) String provider) {
        return ApiResponse.ok(activityService.list(page, pageSize, keyword, state, provider));
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

    @PostMapping("/{id}/end")
    public ApiResponse<EndActivityResponse> end(@PathVariable String id) {
        return ApiResponse.ok(activityService.end(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<DeleteActivityResponse> delete(@PathVariable String id) {
        return ApiResponse.ok(activityService.delete(id));
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
