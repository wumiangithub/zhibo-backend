package com.zhibo.admin.org.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class OrgEmployeeListResponse {
    private int page;
    private int pageSize;
    private long total;
    private List<OrgEmployeeItem> list = new ArrayList<>();
}
