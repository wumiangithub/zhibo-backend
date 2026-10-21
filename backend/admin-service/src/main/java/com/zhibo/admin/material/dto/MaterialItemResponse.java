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
    /** 仅 video：微吼媒资 video_id；未上传可空 */
    private String vhallVideoId;
    /** 仅 video：转码进度 0～100；未上传可空 */
    private Integer vhallTranscodeProgress;
    /**
     * 仅 video：转码结果。空=未上传；0=转码中；1=成功；3=失败
     *（对齐微吼完成回调 method_id=3 的 status）。
     */
    private Integer vhallTranscodeStatus;
}
