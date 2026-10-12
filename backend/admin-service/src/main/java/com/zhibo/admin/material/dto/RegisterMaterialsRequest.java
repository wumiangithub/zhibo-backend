package com.zhibo.admin.material.dto;

import lombok.Data;

import java.util.List;

@Data
public class RegisterMaterialsRequest {
    private String type;
    private String groupId;
    private List<RegisterMaterialItem> items;

    @Data
    public static class RegisterMaterialItem {
        private String name;
        private String url;
        private String objectKey;
        private Long fileSize;
        private String mimeType;
        private String meta;
    }
}
