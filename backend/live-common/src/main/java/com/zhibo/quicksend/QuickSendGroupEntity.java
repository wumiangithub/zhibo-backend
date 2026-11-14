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

/** 快捷发送分组表 {@code quick_send_group}。 */
@Entity
@Table(name = "quick_send_group")
@Getter
@Setter
public class QuickSendGroupEntity {

    @Id
    @Column(length = 32)
    private String id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "is_default", nullable = false)
    private boolean defaultGroup;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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
