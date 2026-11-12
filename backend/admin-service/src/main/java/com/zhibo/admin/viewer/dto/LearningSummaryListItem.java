package com.zhibo.admin.viewer.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 用户学习统计 · 汇总行。 */
@Data
public class LearningSummaryListItem {

    /** 用户中心 uid */
    private String username;
    private String nickname;
    private String avatarUrl;
    private List<Long> tagIds = new ArrayList<>();
    private List<String> tagLabels = new ArrayList<>();
    /** 本轮恒 0。 */
    private int totalLearnDurationSec;
    /** 本轮恒 0。 */
    private int totalLearnDays;
    /** 本轮恒 0。 */
    private int viewedCourseCount;
    /** 本轮恒 0。 */
    private int completedCourseCount;
}
