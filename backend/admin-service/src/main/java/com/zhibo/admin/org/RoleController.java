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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/org/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public ApiResponse<List<RoleListItem>> list() {
        return ApiResponse.ok(roleService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<RoleDetailResponse> detail(@PathVariable String id) {
        return ApiResponse.ok(roleService.detail(parseId(id)));
    }

    @PutMapping("/{id}/menus")
    public ApiResponse<RoleDetailResponse> saveMenus(
            @PathVariable String id,
            @RequestBody SaveRoleMenusRequest request,
            HttpServletRequest httpRequest) {
        return ApiResponse.ok(roleService.saveMenus(parseId(id), request.getMenuCodes(), currentUserId(httpRequest)));
    }

    private static long parseId(String id) {
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("角色 id 无效");
        }
    }

    private static long currentUserId(HttpServletRequest request) {
        Object raw = request.getAttribute(AuthInterceptor.ATTR_USER_ID);
        if (raw instanceof Number number) {
            return number.longValue();
        }
        throw new AuthException(401, "未登录");
    }
}
