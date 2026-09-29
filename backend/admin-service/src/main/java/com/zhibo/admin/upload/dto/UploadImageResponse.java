package com.zhibo.admin.upload.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 图片上传响应：可落库的公网 URL。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadImageResponse {

    /** 微吼返回的 domain_url */
    private String url;
}
