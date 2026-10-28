package com.zhibo.admin.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CreateAudienceResponse {

    private Long id;
    private Long viewerId;
    /** true=新建本场名单行；false=本场已有该观众（可能补写导入人） */
    private boolean created;
}
