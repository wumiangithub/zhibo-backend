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

/** 课程学员行（分享链 enroll / 导入 / 购买）。 */
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

    /** 用户中心 uid（viewer.uid）。 */
    @Column(name = "uid", nullable = false)
    private Long uid;

    /** 1 有权 / 0 已取消。 */
    @Column(name = "entitlement_status", nullable = false)
    private int entitlementStatus = 1;

    /** 权益到期；空=未设。售卖改回长期不清除。 */
    @Column(name = "entitlement_expire_time")
    private LocalDateTime entitlementExpireTime;

    /** purchase / import。 */
    @Column(name = "acquire_way", nullable = false, length = 16)
    private String acquireWay = "purchase";

    /** 添加/导入操作员工。 */
    @Column(name = "importer_id")
    private Long importerId;

    @Column(name = "import_time")
    private LocalDateTime importTime;

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
