package com.zhibo.admin.activity.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AuditListResponse {

    private List<AuditRecordItem> list = new ArrayList<>();
}
