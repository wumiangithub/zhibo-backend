package com.zhibo.admin.activity.dto;

import lombok.Data;

/** 主播 / 归属人展示。id 为十进制字符串。 */
@Data
public class ActivityPerson {

    private String id;
    private String userName;
    /** 头像 URL；无则不输出，前端用默认图。 */
    private String avatar;
    /** 所在部门展示名（多部门顿号连接）；无则不输出。 */
    private String departmentName;
}
