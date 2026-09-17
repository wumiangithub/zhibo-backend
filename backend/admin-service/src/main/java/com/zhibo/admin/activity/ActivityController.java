package com.zhibo.admin.activity;

import com.zhibo.admin.activity.dto.ActivityDetailResponse;
import com.zhibo.admin.activity.dto.ActivityListResponse;
import com.zhibo.admin.activity.dto.CreateActivityRequest;
import com.zhibo.admin.activity.dto.CreateActivityResponse;
import com.zhibo.admin.web.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 活动管理 API。路径对齐 API约定草案；字段用 activity 语义。
 */
@RestController
@RequestMapping("/api/activities")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

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
}
