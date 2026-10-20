package com.zhibo.admin.material.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class MaterialAuditListResponse {
    private List<MaterialAuditRecordItem> list = new ArrayList<>();
}
