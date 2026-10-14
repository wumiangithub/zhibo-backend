package com.zhibo.admin.activity.dto;

import lombok.Data;

/** 归属人或审核人展示。id 为十进制字符串。 */
@Data
public class ActivityPerson {

    private String id;
    private String userName;
}
