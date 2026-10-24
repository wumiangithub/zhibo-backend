package com.zhibo.admin.activity.dto;

import lombok.Getter;
import lombok.Setter;

/** 管理端查 / 重置口令开播口令。 */
@Getter
@Setter
public class ActivityPasscodeResponse {

    /** 9 位数字开播口令。 */
    private String passcode;
}
