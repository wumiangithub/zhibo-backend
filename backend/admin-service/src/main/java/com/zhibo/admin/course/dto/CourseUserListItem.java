package com.zhibo.admin.course.dto;

import lombok.Data;

@Data
public class CourseUserListItem {

    private Long id;
    private Long viewerId;
    private String nickname;
    private String avatar;
    private String phoneMasked;
    /** 观众全局标签 id（未软删） */
    private java.util.List<Long> tagIds = new java.util.ArrayList<>();
    /** 与 tagIds 对齐的标签名 */
    private java.util.List<String> tagLabels = new java.util.ArrayList<>();
    /** 本轮恒 0。 */
    private double chapterCompletionRate;
    private int entitlementStatus;
    /** valid / expired / invalid */
    private String entitlementState;
    private String entitlementStateLabel;
    /** purchase / import */
    private String acquireWay;
    private String acquireWayLabel;
    /** 权益首次获取 / 邀请加入 / 导入时间，均为 created_time */
    private String firstAcquireTime;
    private Long employeeInviterId;
    /** 所属邀请人（仅员工） */
    private String inviterDisplayName;
    /** 同 firstAcquireTime（有邀请人才有） */
    private String inviteTime;
    private Long importerId;
    private String importerName;
    /** 同 firstAcquireTime（有导入人才有） */
    private String importTime;
    /** 库内到期，可空。 */
    private String expireTime;
    /** 展示串：长期 / 时间 / — */
    private String expireDisplay;
}
