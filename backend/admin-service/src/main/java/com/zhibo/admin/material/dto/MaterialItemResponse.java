package com.zhibo.admin.material.dto;

import lombok.Data;

@Data
public class MaterialItemResponse {
    private String id;
    private String type;
    private String groupId;
    private String name;
    private String url;
    private String objectKey;
    private String thumbUrl;
    private String meta;
    private Long fileSize;
    private String status;
    private String uploadedAt;
}
