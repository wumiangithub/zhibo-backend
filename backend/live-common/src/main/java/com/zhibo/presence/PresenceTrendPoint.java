package com.zhibo.presence;

/**
 * 三线图一个时间桶：在线 / 进场 / 离场。
 */
public record PresenceTrendPoint(String time, int online, int enter, int leave) {
}
