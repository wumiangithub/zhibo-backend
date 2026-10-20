package com.zhibo.admin.org.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class DeptTreeNode {
    private Long id;
    private String name;
    private String code;
    private Long parentId;
    private List<DeptTreeNode> children = new ArrayList<>();
}
