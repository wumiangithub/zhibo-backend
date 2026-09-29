package com.zhibo.liveprovider.dto;

/**
 * 全部字段可选；null 表示不更新。
 */
public record UpdateRoomCommand(
        String title,
        String startTime,
        String introduction,
        /** 封面 URL → 微吼 {@code img_url} */
        String coverUrl
) {
}
