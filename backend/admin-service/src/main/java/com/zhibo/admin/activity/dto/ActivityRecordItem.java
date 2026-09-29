package com.zhibo.admin.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回放一条；{@code id} = 微吼 create-demand 的 record_id。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivityRecordItem {

    private String id;
    private String title;
    private String duration;
    private String createdAt;
    private String coverUrl;
}
