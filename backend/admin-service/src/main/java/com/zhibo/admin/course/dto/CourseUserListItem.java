package com.zhibo.admin.course.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class CourseUserListItem {

    private Long id;
    private String nickname;
    private String avatar;
    private String phoneMasked;
    /** 观众全局标签名（未软删） */
    private List<String> tagLabels = new ArrayList<>();
    /** 已完成小节数 / 当前总小节数，百分数一位小数（D101）。 */
    private double chapterCompletionRate;
    private int entitlementStatus;
    /** valid / expired / invalid */
    private String entitlementState;
    private String entitlementStateLabel;
    /** 购买 / 导入 */
    private String acquireWayLabel;
    /** 权益首次获取时间 = created_time */
    private String firstAcquireTime;
    /** 库内到期，可空。 */
    private String expireTime;
    /** 展示串：长期 / 时间 / — */
    private String expireDisplay;
}
