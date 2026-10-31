package com.zhibo.admin.activity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** PUT /api/v1/activities/sales/{id} */
@Data
public class SalesUpdateRequest {

    @NotNull(message = "sellAlone 不能为空")
    private Boolean sellAlone;

    @NotBlank(message = "sellType 不能为空")
    private String sellType;

    /** encrypted 时必填；恰好 6 位数字明文 */
    private String password;

    /** free 时服务端强制 long；其余必填 long / days */
    private String accessValidityType;

    /** type=days 时必填，≥1 */
    private Integer accessValidityDays;
}
