package com.zhibo.admin.employee.dto;

import lombok.Data;

@Data
public class EmployeeItem {

    /** 对应 rs_mainkey_id（JSON 数字）。 */
    private Long id;
    private String userName;
    /** 头像 URL；无则不输出，前端用默认图。 */
    private String avatar;
    /** 所在部门展示名（多部门顿号连接）；无则不输出。 */
    private String departmentName;
}
