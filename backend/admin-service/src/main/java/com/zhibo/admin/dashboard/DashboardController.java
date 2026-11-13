package com.zhibo.admin.dashboard;

import com.zhibo.admin.dashboard.dto.BusinessMetricsResponse;
import com.zhibo.admin.dashboard.dto.RecentLivesResponse;
import com.zhibo.admin.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台概况。须管理端登录（{@code /api/v1/**} 默认鉴权，未进白名单）。
 * 经营数据只读 viewer / activity_order；最近直播只读本站 activity / presence / replay / viewer。
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardBusinessService dashboardBusinessService;
    private final DashboardRecentLivesService dashboardRecentLivesService;

    @GetMapping("/business-metrics")
    public ApiResponse<BusinessMetricsResponse> businessMetrics() {
        return ApiResponse.ok(dashboardBusinessService.metrics());
    }

    @GetMapping("/recent-lives")
    public ApiResponse<RecentLivesResponse> recentLives(
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        return ApiResponse.ok(dashboardRecentLivesService.recentLives(page, pageSize));
    }
}
