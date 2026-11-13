package com.zhibo.notification;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 管理端通知收件箱 {@code admin_notification_inbox}。 */
@Entity
@Table(name = "admin_notification_inbox")
@Getter
@Setter
public class AdminNotificationInboxEntity {

    @EmbeddedId
    private AdminNotificationInboxId id;

    /** 空 = 未读 */
    @Column(name = "read_time")
    private LocalDateTime readTime;
}
