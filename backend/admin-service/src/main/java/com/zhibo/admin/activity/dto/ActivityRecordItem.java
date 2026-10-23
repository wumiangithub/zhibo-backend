package com.zhibo.admin.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回放一条；{@code id} = 微吼 create-demand 的 record_id（JSON number）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityRecordItem {

    private Long id;
    private String title;
    private String duration;
    private String createdTime;
    private String coverUrl;
    /** 体积（KB），微吼 storage */
    private String storage;
    /** 到期时间，微吼 save_time */
    private String saveTime;
}
