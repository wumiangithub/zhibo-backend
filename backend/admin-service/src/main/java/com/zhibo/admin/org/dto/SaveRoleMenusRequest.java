package com.zhibo.admin.org.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class SaveRoleMenusRequest {
    private List<String> menuCodes = new ArrayList<>();
}
