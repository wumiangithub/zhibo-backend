package com.zhibo.admin.activity.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** 口令开播进房（H5 / App）：凭口令换主播参会地址。 */
@Getter
@Setter
public class PasscodeEnterRequest {

    /** 开播口令（9 位数字）。 */
    @NotBlank(message = "开播口令不能为空")
    private String passcode;

    /** 讲师名称；空则「讲师」。 */
    private String nickname;
}
