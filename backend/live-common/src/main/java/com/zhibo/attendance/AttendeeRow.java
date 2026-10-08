package com.zhibo.attendance;

/** 名单里的一个人。online 一定有值。 */
public record AttendeeRow(
        String userId,
        String nickname,
        String avatarUrl,
        String enterTime,
        String leaveTime,
        boolean online) {
}
