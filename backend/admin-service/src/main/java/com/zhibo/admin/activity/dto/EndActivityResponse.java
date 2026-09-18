package com.zhibo.admin.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EndActivityResponse {

    private Long id;
    /** 结束后的状态（通常为 3 结束） */
    private Integer state;
}
