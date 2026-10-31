package com.zhibo.admin.course.dto;

import lombok.Data;

import java.util.List;

@Data
public class CatalogUsedContentsResponse {

    /** 本课已占用的直播 activity id */
    private List<String> lives;
    /** 本课已占用的素材 id（图片/视频/文档/文章） */
    private List<String> materials;
}
