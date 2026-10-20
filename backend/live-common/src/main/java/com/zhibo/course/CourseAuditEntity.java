package com.zhibo.course;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** 课程风控审核记录。 */
@Entity
@Table(name = "course_audit")
@Getter
@Setter
public class CourseAuditEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "course_id", nullable = false, length = 36)
    private String courseId;

    /** submit 发起申请 / audit 审核 */
    @Column(nullable = false, length = 16)
    private String action;

    /** passed / rejected；submit 为空 */
    @Column(length = 16)
    private String result;

    @Column(length = 200)
    private String reason;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "operator_name", nullable = false, length = 50)
    private String operatorName;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @PrePersist
    void assignIdIfMissing() {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
    }
}
