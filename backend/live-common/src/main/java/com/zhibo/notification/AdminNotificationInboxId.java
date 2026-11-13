package com.zhibo.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/** {@code admin_notification_inbox} 联合主键。 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AdminNotificationInboxId implements Serializable {

    @Column(name = "notification_id", length = 32)
    private String notificationId;

    @Column(name = "staff_id")
    private Long staffId;
}
