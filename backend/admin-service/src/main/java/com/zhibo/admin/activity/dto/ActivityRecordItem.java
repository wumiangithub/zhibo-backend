package com.zhibo.admin.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 回放一条；{@code id} = 微吼 create-demand 的 record_id（JSON number）。
 * D88：不再回 {@code saveTime}；本库有 {@code tos_url} 时带 {@code tosUrl}；
 * {@code ready} 时另带 {@code downloadUrl}（可与 {@code tosUrl} 同值，兼容旧前端）。
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
    /** 体积（KB）字符串 */
    private String storage;
    /** 本库状态 INT：1 pending_package / 2 packaging / 3 downloading / 4 ready / 5 failed；仅云侧尚未入库时可省略 */
    private Integer status;
    /** 仅本库 ready 时为本站 TOS/CDN URL（与 tosUrl 同值） */
    private String downloadUrl;
    /** 本库有 tos_url 时返回；前端可据此直接下载 */
    private String tosUrl;
}
