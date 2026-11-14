package com.zhibo.attendance;

import java.util.List;

/**
 * 中控台名单一页。空的 nickname / avatarUrl / leaveTime / roleLabel 由 Jackson 省略。
 * <p>
 * 层级：一级 tab（全部/在线/离线）→ 二级 kind（学员/助教，是当前 tab 子集）。
 * allTotal / onlineTotal / offlineTotal：仅关键字后的一级角标（不受 kind / 跟进人 / 行为）。
 * studentTotal / assistantTotal：当前一级 tab 内的二级人数（学员再套跟进人/行为；助教不套）。
 */
public record AttendeePage(
        Integer activityState,
        long total,
        long studentTotal,
        long assistantTotal,
        long allTotal,
        long onlineTotal,
        long offlineTotal,
        int page,
        int pageSize,
        List<AttendeeRow> list) {
}
