package com.zhibo.admin.console.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/** 中控台员工数据一行（D125）：本场专属链邀请人聚合。 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ConsoleStaffDetailItem {

    private Long employeeId;
    private String employeeName;
    private Long deptId;
    private String deptName;
    private int inviteCount;
    private int liveWatchCount;
    private long avgLiveWatchDurationSec;
    private int onlineCount;
    private int commentCount;
}
