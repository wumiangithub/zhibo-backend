package com.zhibo.admin.material.dto;

import lombok.Data;

@Data
public class MoveMaterialRequest {
    /** 目标分组 UUID，须与素材同类型 */
    private String groupId;
}
