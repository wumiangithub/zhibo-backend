package com.zhibo.admin.viewer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateViewerBlacklistResponse {

    private Long id;
    private int banComment;
    private int banPurchase;
    private int banAccess;
    private int status;
}
