package com.zhibo.live.watch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LeaveAttendanceResponse {

    /** 本次新写下离开时间的条数。 */
    private int updated;
    /** 已有离开时间、未改动的条数。 */
    private int skipped;
    /** 没有对应进房记录的条数。 */
    private int missing;
}
