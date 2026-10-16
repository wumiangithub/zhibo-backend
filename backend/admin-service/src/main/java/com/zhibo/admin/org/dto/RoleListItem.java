package com.zhibo.admin.org.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RoleListItem {
    /** 角色主键 {@code sys_uimsrole.rs_mainkey_id}，JSON 数字 */
    private Long id;
    private String name;
}
