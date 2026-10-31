package com.zhibo.admin.course.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CatalogNodeView {

    private Long id;
    private String courseId;
    private Long parentId;
    /** 1 章节 / 2 子课程 / 3 小节 */
    private Integer nodeType;
    private String title;
    private String coverMaterialId;
    private String coverUrl;
    /** 仅小节：1 直播 / 2 图片 / 3 视频 / 4 文档 / 5 文章 */
    private Integer contentKind;
    private String contentId;
    private Integer sortOrder;
    /** 仅子课程：直接下级章节数 */
    private Integer chapterCount;
    /** 仅子课程：子孙小节数 */
    private Integer lessonCount;
}
