package com.zhibo.admin.org.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OrgEmployeeItem {
    private String id;
    private String userName;
    private String departmentName;
    private boolean liveEnabled;
    /** normal / disabled / unknown */
    private String hqStatus;
}
