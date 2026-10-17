package com.zhibo.admin.org;

import com.zhibo.admin.auth.AuthException;
import com.zhibo.admin.auth.AuthInterceptor;
import com.zhibo.admin.org.dto.RoleDetailResponse;
import com.zhibo.admin.org.dto.RoleListItem;
import com.zhibo.admin.org.dto.SaveRoleMenusRequest;
import com.zhibo.admin.web.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/list")
    public ApiResponse<List<RoleListItem>> list() {
        return ApiResponse.ok(roleService.list());
    }

    @GetMapping("/detail/{id}")
    public ApiResponse<RoleDetailResponse> detail(@PathVariable Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("角色 id 无效");
        }
        return ApiResponse.ok(roleService.detail(id));
    }

    @PostMapping("/save")
    public ApiResponse<RoleDetailResponse> save(
            @RequestBody SaveRoleMenusRequest request,
            HttpServletRequest httpRequest) {
        Long id = request.getId();
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("角色 id 无效");
        }
        return ApiResponse.ok(roleService.saveMenus(id, request.getMenuCodes(), currentUserId(httpRequest)));
    }

    private static long currentUserId(HttpServletRequest request) {
        Object raw = request.getAttribute(AuthInterceptor.ATTR_USER_ID);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        throw new AuthException(401, "未登录");
    }
}
