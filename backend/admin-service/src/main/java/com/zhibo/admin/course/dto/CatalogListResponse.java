package com.zhibo.admin.course.dto;

import lombok.Data;

import java.util.List;

@Data
public class CatalogListResponse {

    private List<CatalogNodeView> list;
}
