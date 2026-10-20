package com.zhibo.admin.org;

import com.zhibo.admin.auth.AuthException;
import com.zhibo.admin.auth.AuthInterceptor;
import com.zhibo.admin.org.dto.DeptTreeNode;
import com.zhibo.admin.org.dto.OrgEmployeeListResponse;
import com.zhibo.admin.org.dto.SetLiveEnabledRequest;
import com.zhibo.admin.org.dto.SetLiveEnabledResponse;
import com.zhibo.admin.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/org")
@RequiredArgsConstructor
public class OrgController {

    private final OrgService orgService;

    @GetMapping("/departments/tree")
    public ApiResponse<List<DeptTreeNode>> departmentTree() {
        return ApiResponse.ok(orgService.departmentTree());
    }

    @GetMapping("/employees")
    public ApiResponse<OrgEmployeeListResponse> listEmployees(
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false) String userName,
            @RequestParam(required = false) Long roleId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return ApiResponse.ok(orgService.listEmployees(deptId, userName, roleId, page, pageSize));
    }

    @PutMapping("/employees/live-enabled/{id}")
    public ApiResponse<SetLiveEnabledResponse> setLiveEnabled(
            @PathVariable String id,
            @RequestBody SetLiveEnabledRequest request,
            HttpServletRequest httpRequest) {
        long userId;
        try {
            userId = Long.parseLong(id);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("员工 id 无效");
        }
        return ApiResponse.ok(orgService.setLiveEnabled(userId, request.getEnabled(), currentUserId(httpRequest)));
    }

    private static long currentUserId(HttpServletRequest request) {
        Object raw = request.getAttribute(AuthInterceptor.ATTR_USER_ID);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        throw new AuthException(401, "未登录");
    }
}
