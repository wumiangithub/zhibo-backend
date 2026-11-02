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

/** 课程学员行（分享链 enroll）。 */
@Entity
@Table(name = "course_user")
@Getter
@Setter
public class CourseUserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", nullable = false, length = 32)
    private String courseId;

    @Column(name = "viewer_id", nullable = false)
    private Long viewerId;

    /** 1 有效 / 0 无权益。 */
    @Column(name = "entitlement_status", nullable = false)
    private int entitlementStatus = 1;

    @Column(name = "employee_inviter_id")
    private Long employeeInviterId;

    @Column(name = "employee_invite_time")
    private LocalDateTime employeeInviteTime;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;
}
