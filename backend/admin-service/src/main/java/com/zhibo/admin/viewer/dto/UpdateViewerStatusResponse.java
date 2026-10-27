package com.zhibo.admin.viewer.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateViewerStatusResponse {

    private Long id;
    private int status;
}
