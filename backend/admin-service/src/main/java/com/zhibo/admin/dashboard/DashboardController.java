package com.zhibo.admin.dashboard;

import com.zhibo.admin.dashboard.dto.BusinessMetricsResponse;
import com.zhibo.admin.web.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 工作台概况「经营数据」。须管理端登录（{@code /api/v1/**} 默认鉴权，未进白名单）。
 * 只读本站 viewer / activity_order，不查 OMS。
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardBusinessService dashboardBusinessService;

    @GetMapping("/business-metrics")
    public ApiResponse<BusinessMetricsResponse> businessMetrics() {
        return ApiResponse.ok(dashboardBusinessService.metrics());
    }
}
