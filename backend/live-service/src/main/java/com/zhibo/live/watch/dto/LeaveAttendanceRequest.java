package com.zhibo.live.watch.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class LeaveAttendanceRequest {

    @NotNull(message = "items 不能为空")
    @Size(min = 1, max = 100, message = "items 长度须在 1～100")
    @Valid
    private List<LeaveAttendanceItem> items;
}
