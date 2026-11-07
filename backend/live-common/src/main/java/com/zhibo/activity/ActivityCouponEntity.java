package com.zhibo.activity;

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

/** 本场已发送优惠券（一场一券一行；D113）。 */
@Entity
@Table(name = "activity_coupon")
@Getter
@Setter
public class ActivityCouponEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false, length = 32)
    private String activityId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "coupon_no", nullable = false, length = 64)
    private String couponNo;

    @Column(name = "coupon_name", nullable = false, length = 256)
    private String couponName;

    @Column(name = "coupon_amount", nullable = false, length = 32)
    private String couponAmount;

    @Column(name = "coupon_start_date", nullable = false, length = 32)
    private String couponStartDate;

    @Column(name = "coupon_end_date", nullable = false, length = 32)
    private String couponEndDate;

    @Column(name = "all_sku", nullable = false)
    private boolean allSku;

    @Column(name = "dept_names", length = 512)
    private String deptNames;

    @Column(name = "sku_names", length = 1024)
    private String skuNames;

    @Column(name = "last_sender_employee_id", nullable = false)
    private Long lastSenderEmployeeId;

    @Column(name = "last_sender_nickname", length = 64)
    private String lastSenderNickname;

    /** 岗位表解析的部门 id；无岗位可空。 */
    @Column(name = "last_sender_dept_id")
    private Long lastSenderDeptId;

    @Column(name = "last_sender_dept_name", length = 60)
    private String lastSenderDeptName;

    @Column(name = "last_sent_time", nullable = false)
    private LocalDateTime lastSentTime;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;
}
