package com.zhibo.admin.org.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrgEmployeeItem {
    private Long id;
    private String userName;
    private String departmentName;
    /** 在用角色名，多角色顿号；无则为空串 */
    private String roleNames = "";
    private boolean liveEnabled;
    /** normal / disabled / unknown */
    private String hqStatus;
}
