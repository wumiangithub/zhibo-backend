package com.zhibo.admin.material.dto;

import lombok.Data;

import java.util.List;

@Data
public class MaterialListResponse {
    private long total;
    private int page;
    private int pageSize;
    private List<MaterialItemResponse> list;
}
