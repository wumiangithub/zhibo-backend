package com.zhibo.admin.viewer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateViewerBlacklistRequest {

    @NotNull
    private Integer banComment;

    @NotNull
    private Integer banPurchase;

    @NotNull
    private Integer banAccess;
}
