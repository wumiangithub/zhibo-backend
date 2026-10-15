package com.zhibo.admin.material.dto;

import lombok.Data;

@Data
public class MaterialItemResponse {
    private String id;
    private String type;
    private String groupId;
    private String name;
    private String url;
    private String objectKey;
    private String thumbUrl;
    private String meta;
    private Long fileSize;
    private String status;
    /** pending / passed / rejected */
    private String aiAuditStatus;
    /** pending / passed / rejected */
    private String riskAuditStatus;
    /** 上传人员工 id（字符串）；旧行可空 */
    private String uploaderId;
    /** 上传人姓名快照 */
    private String uploaderName;
    /** 所在部门展示名快照 */
    private String departmentName;
    private String uploadedAt;
}
