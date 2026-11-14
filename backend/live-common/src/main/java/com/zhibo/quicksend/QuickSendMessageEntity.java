package com.zhibo.quicksend;

import com.zhibo.common.UuidIds;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** 快捷发送话术表 {@code quick_send_message}。 */
@Entity
@Table(name = "quick_send_message")
@Getter
@Setter
public class QuickSendMessageEntity {

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "group_id", nullable = false, length = 32)
    private String groupId;

    @Column(nullable = false, length = 100)
    private String content;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "deleted_time")
    private LocalDateTime deletedTime;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;

    @PrePersist
    void assignIdIfMissing() {
        if (id == null) {
            id = UuidIds.newId();
        }
    }
}
