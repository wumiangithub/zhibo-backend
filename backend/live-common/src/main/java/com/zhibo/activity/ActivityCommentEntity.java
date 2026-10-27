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

/** 本站直播评论流水（D77）。 */
@Entity
@Table(name = "activity_comment")
@Getter
@Setter
public class ActivityCommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false, length = 32)
    private String activityId;

    @Column(name = "viewer_id")
    private Long viewerId;

    @Column(name = "guest_id", length = 64)
    private String guestId;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(length = 64)
    private String nickname;

    @Column(name = "role_name")
    private Integer roleName;

    @Column(name = "provider_msg_id", length = 64)
    private String providerMsgId;

    @Column(name = "comment_time", nullable = false)
    private LocalDateTime commentTime;

    @Column(name = "deleted_time")
    private LocalDateTime deletedTime;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;
}
