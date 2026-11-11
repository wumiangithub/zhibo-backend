package com.zhibo.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** 课程小节学习进度（D101）。 */
@Entity
@Table(name = "course_lesson_progress")
@Getter
@Setter
public class CourseLessonProgressEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false, length = 32)
    private String courseId;

    @Column(name = "lesson_node_id", nullable = false)
    private Long lessonNodeId;

    /** 用户中心 uid（viewer.uid）。 */
    @Column(name = "uid", nullable = false)
    private Long uid;

    /** {@link CourseLessonParticipateStatus} */
    @Column(name = "participate_status", nullable = false)
    private int participateStatus = CourseLessonParticipateStatus.NOT_STARTED;

    @Column(name = "learn_duration_sec", nullable = false)
    private long learnDurationSec = 0L;

    @Column(name = "progress_bar_sec")
    private Integer progressBarSec;

    @Column(name = "progress_percent", nullable = false)
    private int progressPercent = 0;

    @Column(name = "completed_time")
    private LocalDateTime completedTime;

    @Column(name = "first_participate_time")
    private LocalDateTime firstParticipateTime;

    @Column(name = "last_learn_time")
    private LocalDateTime lastLearnTime;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;
}
