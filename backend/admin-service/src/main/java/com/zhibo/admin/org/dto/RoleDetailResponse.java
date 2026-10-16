package com.zhibo.admin.org.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RoleDetailResponse {
    /** 角色主键 {@code sys_uimsrole.rs_mainkey_id}，JSON 数字 */
    private Long id;
    private String name;
    private String remark;
    private List<RoleMemberItem> members = new ArrayList<>();
    private List<RoleMenuNode> menus = new ArrayList<>();
    /** 已勾选的本站菜单编码（含父级与叶子） */
    private List<String> menuCodes = new ArrayList<>();
}
