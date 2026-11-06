package com.zhibo.admin.viewer.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** 用户学习统计 · 课程数据行（D112：小节维度）。 */
@Data
public class LearningCourseListItem {

    /** {@code course_lesson_progress.id} */
    private long id;
    private long viewerId;
    private String nickname;
    private String avatarUrl;
    private List<Long> tagIds = new ArrayList<>();
    private List<String> tagLabels = new ArrayList<>();
    /** 本站课程 UUID */
    private String courseId;
    private long lessonNodeId;
    private String lessonName;
    private int totalLearnDurationSec;
    private int progressPercent;
    private String firstLearnTime;
    private String completedTime;
    /** 直播 / 图片 / 视频 / 文档 / 文章 */
    private String lessonType;
    /** 视频素材时长秒；非视频或解不出 → 0 */
    private int lessonDurationSec;
}
