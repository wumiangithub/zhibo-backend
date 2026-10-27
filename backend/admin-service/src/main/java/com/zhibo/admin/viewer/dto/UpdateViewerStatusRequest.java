package com.zhibo.admin.viewer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateViewerStatusRequest {

    /** 1 正常 / 0 停用 */
    @NotNull(message = "status 不能为空")
    private Integer status;
}
