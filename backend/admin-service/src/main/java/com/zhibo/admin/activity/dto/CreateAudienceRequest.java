package com.zhibo.admin.activity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateAudienceRequest {

    @NotBlank(message = "手机号不能为空")
    private String phone;
}
