package com.zhibo.attendance;

/** 名单里的一个人。online 一定有值。username = 库列 username（邮箱本地部分）。 */
public record AttendeeRow(
        String username,
        String nickname,
        String avatarUrl,
        String enterTime,
        String leaveTime,
        boolean online) {
}
