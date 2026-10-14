package com.zhibo.admin.employee.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class EmployeeListResponse {

    private List<EmployeeItem> list = new ArrayList<>();
}
