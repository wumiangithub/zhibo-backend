package com.zhibo.admin.material.dto;

import lombok.Data;

/** 裁剪后替换图片文件。不改展示名与分组。 */
@Data
public class ReplaceImageRequest {
    private String url;
    private String objectKey;
    private Long fileSize;
    private String meta;
}
