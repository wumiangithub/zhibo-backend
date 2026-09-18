package com.zhibo.admin.activity;

import com.zhibo.admin.activity.dto.ActivityDetailResponse;
import com.zhibo.admin.activity.dto.ActivityEmbedResponse;
import com.zhibo.admin.activity.dto.ActivityHostResponse;
import com.zhibo.admin.activity.dto.ActivityListResponse;
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

/**
 * 活动管理 API。路径对齐 API约定；字段用 activity 语义。
 */
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

    /**
     * @param page     对应微吼 pos，从 0 开始
     * @param pageSize 对应微吼 limit，最大 100
     * @param keyword  标题或活动 id（微吼 title）
     * @param state    活动状态：0全部 1直播 2预告 3结束 4点播 5回放
     */
    @GetMapping
    public ApiResponse<ActivityListResponse> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer state) {
        return ApiResponse.ok(activityService.list(page, pageSize, keyword, state));
    }

    @GetMapping("/{id}")
    public ApiResponse<ActivityDetailResponse> detail(@PathVariable long id) {
        return ApiResponse.ok(activityService.detail(id));
    }

    /** 改标题 / 开始时间（type 不可改）。 */
    @PutMapping("/{id}")
    public ApiResponse<CreateActivityResponse> update(
            @PathVariable long id,
            @RequestBody UpdateActivityRequest request) {
        return ApiResponse.ok(activityService.update(id, request));
    }

    /** 结束直播。 */
    @PostMapping("/{id}/end")
    public ApiResponse<EndActivityResponse> end(@PathVariable long id) {
        return ApiResponse.ok(activityService.end(id));
    }

    /** 删除活动。 */
    @DeleteMapping("/{id}")
    public ApiResponse<DeleteActivityResponse> delete(@PathVariable long id) {
        return ApiResponse.ok(activityService.delete(id));
    }

    /** 主持人开播链接（新窗口打开，不要 iframe）。 */
    @GetMapping("/{id}/host")
    public ApiResponse<ActivityHostResponse> host(@PathVariable long id) {
        return ApiResponse.ok(hostService.host(id));
    }

    /** 管理后台嵌入（iframe；此页不能开播）。 */
    @GetMapping("/{id}/embed")
    public ApiResponse<ActivityEmbedResponse> embed(@PathVariable long id) {
        return ApiResponse.ok(embedService.embed(id));
    }
}
