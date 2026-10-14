package com.zhibo.activity;

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

/** 活动风控审核记录。 */
@Entity
@Table(name = "activity_audit")
@Getter
@Setter
public class ActivityAuditEntity {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "activity_id", nullable = false, length = 36)
    private String activityId;

    /** passed / rejected */
    @Column(nullable = false, length = 16)
    private String result;

    @Column(length = 200)
    private String reason;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "operator_name", nullable = false, length = 50)
    private String operatorName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void assignIdIfMissing() {
        if (id == null) {
            id = UUID.randomUUID().toString();
        }
    }
}
