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

/**
 * 开播场次表 {@code activity_live_session}（D119）。
 * 一行 = 一场活动 + 微吼一次开/结束；行主键不对外。
 * {@code switch_id=0} 表示 live-state 回调临时未闭合段（尚无云 id）。
 */
@Entity
@Table(name = "activity_live_session")
@Getter
@Setter
public class ActivityLiveSessionEntity {

    /** live-state 开播时尚未拿到云 switch_id 的临时段。 */
    public static final long PROVISIONAL_SWITCH_ID = 0L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false, length = 32)
    private String activityId;

    @Column(nullable = false, length = 32)
    private String provider;

    @Column(name = "switch_id", nullable = false)
    private Long switchId;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** ← switch_list[].type；0 直播 / 1 录制 / 2 彩排（以微吼为准）。 */
    @Column(name = "session_type")
    private Integer sessionType;

    @Column(name = "start_type")
    private Integer startType;

    @Column(name = "end_type")
    private Integer endType;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;
}
