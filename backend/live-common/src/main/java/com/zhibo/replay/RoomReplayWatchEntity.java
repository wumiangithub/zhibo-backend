package com.zhibo.replay;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** 回放观看：一次播放一行。 */
@Entity
@Table(name = "room_replay_watch")
@Getter
@Setter
public class RoomReplayWatchEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false, length = 32)
    private String roomId;

    @Column(name = "viewer_id", nullable = false)
    private Long viewerId;

    @Column(name = "record_id", nullable = false)
    private Long recordId;

    @Column(name = "socket_id", nullable = false, length = 32)
    private String socketId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "last_report_time", nullable = false)
    private LocalDateTime lastReportTime;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;
}
