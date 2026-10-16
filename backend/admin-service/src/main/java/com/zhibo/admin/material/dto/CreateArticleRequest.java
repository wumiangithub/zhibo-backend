package com.zhibo.admin.material.dto;

import lombok.Data;

@Data
public class CreateArticleRequest {
    private String groupId;
    /** 标题，去空白后 1～45 */
    private String title;
    /** 作者，去空白后 1～16 */
    private String author;
    /** 富文本 HTML，非空且不超过 100000 字符 */
    private String content;
}
