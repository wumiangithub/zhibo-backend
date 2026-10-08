package com.zhibo.attendance;

import java.util.List;

/** 中控台名单一页。空的 nickname / avatarUrl / leaveTime 由 Jackson 省略。 */
public record AttendeePage(
        Integer activityState,
        long total,
        int page,
        int pageSize,
        List<AttendeeRow> list) {
}
