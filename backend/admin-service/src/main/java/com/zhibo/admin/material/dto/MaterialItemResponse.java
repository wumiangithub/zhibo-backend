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
    /** 0 待审核 / 1 已通过 / 2 已驳回 */
    private Integer aiAuditStatus;
    /** 0 待审核 / 1 已通过 / 2 已驳回 */
    private Integer riskAuditStatus;
    /** 上传人员工 id；旧行可空 */
    private Long uploaderId;
    /** 上传人姓名快照 */
    private String uploaderName;
    /** 所在部门展示名快照 */
    private String departmentName;
    private String uploadedTime;
}
