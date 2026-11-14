package com.zhibo.admin.attendance.dto;

import lombok.Data;

import java.util.List;

@Data
public class AttendeeListResponse {

    private Integer activityState;
    private long total;
    /** 二级：当前一级 tab 内学员人数（含跟进人/行为）。 */
    private long studentTotal;
    /** 二级：当前一级 tab 内助教人数。 */
    private long assistantTotal;
    /** 一级：关键字后全部人数（不受 kind / 跟进人 / 行为）。 */
    private long allTotal;
    /** 一级：关键字后在线人数。 */
    private long onlineTotal;
    /** 一级：关键字后离线人数。 */
    private long offlineTotal;
    private int page;
    private int pageSize;
    private List<AttendeeItem> list;
}
