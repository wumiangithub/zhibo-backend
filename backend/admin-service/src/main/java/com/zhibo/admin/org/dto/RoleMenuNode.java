package com.zhibo.admin.org.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RoleMenuNode {
    private String code;
    private String name;
    private List<RoleMenuNode> children = new ArrayList<>();
}
