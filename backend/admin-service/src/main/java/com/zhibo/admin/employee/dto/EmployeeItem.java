package com.zhibo.admin.employee.dto;

import lombok.Data;

@Data
public class EmployeeItem {

    /** 十进制字符串，对应 rs_mainkey_id。 */
    private String id;
    private String userName;
}
