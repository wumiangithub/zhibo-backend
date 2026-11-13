package com.zhibo.notification;

import com.zhibo.common.UuidIds;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 管理端通知正文 {@code admin_notification}。 */
@Entity
@Table(name = "admin_notification")
@Getter
@Setter
public class AdminNotificationEntity {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false)
    private Integer type;

    @Column(name = "audit_result", nullable = false)
    private Integer auditResult;

    @Column(nullable = false, length = 32)
    private String category;

    @Column(nullable = false, length = 64)
    private String title;

    @Column(nullable = false, length = 512)
    private String body;

    @Column(name = "ref_type", nullable = false)
    private Integer refType;

    @Column(name = "ref_id", nullable = false, length = 32)
    private String refId;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @PrePersist
    void assignIdIfMissing() {
        if (id == null) {
            id = UuidIds.newId();
        }
    }
}
