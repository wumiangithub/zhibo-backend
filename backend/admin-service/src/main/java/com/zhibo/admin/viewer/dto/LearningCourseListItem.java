package com.zhibo.admin.viewer.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 用户学习统计 · 课程行（形状预留）。
 * 本轮接口恒空列表，不实际填充。
 */
@Data
public class LearningCourseListItem {

    private long id;
    private long viewerId;
    private String nickname;
    private String avatarUrl;
    private List<Long> tagIds = new ArrayList<>();
    private List<String> tagLabels = new ArrayList<>();
    private String courseId;
    private String courseName;
    private int totalLearnDurationSec;
    private int progressPercent;
    private String firstLearnTime;
    private String completedTime;
    private String courseType;
    private int courseDurationSec;
}
