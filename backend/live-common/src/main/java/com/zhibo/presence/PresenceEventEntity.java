package com.zhibo.presence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import com.zhibo.common.UuidIds;

/** 进出流水：每次 Join/Leave 一行，不覆盖。 */
@Entity
@Table(name = "presence_event")
@Getter
@Setter
public class PresenceEventEntity {

    public static final String TYPE_JOIN = "Join";
    public static final String TYPE_LEAVE = "Leave";
    public static final String SOURCE_CALLBACK = "callback";
    public static final String SOURCE_CLIENT = "client";

    @Id
    @Column(length = 32)
    private String id;

    @Column(name = "room_id", nullable = false, length = 32)
    private String roomId;

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(length = 64)
    private String mid;

    @Column(nullable = false, length = 16)
    private String source;

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
