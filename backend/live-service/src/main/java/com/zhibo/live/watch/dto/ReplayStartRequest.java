package com.zhibo.live.watch.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class ReplayStartRequest {

    @NotNull(message = "recordId 不能为空")
    @Positive(message = "recordId 须为正整数")
    private Long recordId;
}
