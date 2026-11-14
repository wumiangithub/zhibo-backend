package com.zhibo.admin.quicksend.dto;

import lombok.Data;

@Data
public class CreateQuickSendMessageRequest {
    private String groupId;
    private String content;
    /** 相对插入锚点；省略则追加到组末 */
    private String anchorId;
    /** above / below；有 anchorId 时必填 */
    private String place;
}
