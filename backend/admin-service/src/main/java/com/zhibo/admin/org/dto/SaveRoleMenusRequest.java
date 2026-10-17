package com.zhibo.admin.org.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SaveRoleMenusRequest {
    /** 角色主键 {@code sys_uimsrole.rs_mainkey_id} */
    private Long id;
    private List<String> menuCodes = new ArrayList<>();
}
