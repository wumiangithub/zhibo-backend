package com.zhibo.admin.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthUserResponse {
    private Long id;
    private String userAccount;
    private String userName;
    private String avatar;
    /** 所在部门展示名（多部门顿号连接）；无则不输出。 */
    private String departmentName;
}
