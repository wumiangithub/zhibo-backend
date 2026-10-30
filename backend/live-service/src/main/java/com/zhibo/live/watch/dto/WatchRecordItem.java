package com.zhibo.live.watch.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WatchRecordItem {

    /** 云侧回放 id，与管理端 records list[].id 相同 */
    private Long id;
    /** 回放标题 */
    private String title;
    /** 时长，HH:mm:ss */
    private String duration;
    /** 封面 */
    private String coverUrl;
    /** 本站 TOS/CDN 成片地址；可直接给 &lt;video src&gt; */
    private String tosUrl;
    /** 云侧生成时间，yyyy-MM-dd HH:mm:ss */
    private String createdTime;
}
