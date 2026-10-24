package com.zhibo.admin.course.dto;

import lombok.Data;

/** 创建课程课件项：素材 id + 学员权限。 */
@Data
public class CourseMaterialInput {

    /** 本站素材 UUID */
    private String id;

    /**
     * 学员权限：1 预览及下载 / 2 仅预览 / 3 仅下载。
     * 省略时按扩展名推断（压缩包→3，其余→1）。
     */
    private Integer accessPermission;
}
