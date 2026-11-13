package com.zhibo.activity;

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

/**
 * 同一房间、同一用户只有一行。再次进入更新进入时间并清空离开时间，不插新行。
 */
@Entity
@Table(name = "room_attendance")
@Getter
@Setter
public class RoomAttendanceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 本站活动 UUID。 */
    @Column(name = "room_id", nullable = false, length = 32)
    private String roomId;

    /** 邮箱本地部分：观众 username / e{员工id} / z{员工id}。 */
    @Column(nullable = false, length = 128)
    private String username;

    @Column(length = 128)
    private String nickname;

    @Column(name = "avatar_url", length = 1024)
    private String avatarUrl;

    @Column(name = "enter_time", nullable = false)
    private LocalDateTime enterTime;

    @Column(name = "leave_time")
    private LocalDateTime leaveTime;

    /** 首次入库时间。再次进入不改。 */
    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;
}
