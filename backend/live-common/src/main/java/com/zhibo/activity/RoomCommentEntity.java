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

/** 本站直播评论流水（D77；表名 room_comment）。 */
@Entity
@Table(name = "room_comment")
@Getter
@Setter
public class RoomCommentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false, length = 32)
    private String activityId;

    /** 直播云：vhall / aliyun / tencent（同 activity.provider；V66 冗余）。 */
    @Column(name = "provider", length = 32)
    private String provider;

    /** 云房间号（同 activity.provider_room_id；V66 冗余为 room_id）。 */
    @Column(name = "room_id", length = 64)
    private String roomId;

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

    /** 1 聊天 / 2 提问。见 {@link RoomCommentType}。 */
    @Column(name = "comment_type", nullable = false)
    private Integer commentType;

    /** 云消息 id（如微吼 msg_id；聊天历史行 / 提问 create 后的 data.msg_id）。 */
    @Column(name = "msg_id", length = 64)
    private String msgId;

    /** 云提问 id（如微吼 data.id）；提问行幂等与回答对齐键（D107）。 */
    @Column(name = "question_id", length = 64)
    private String questionId;

    /** 主持人/嘉宾回复正文（提问行；D107）。 */
    @Column(name = "answer_content", length = 500)
    private String answerContent;

    @Column(name = "answer_nickname", length = 64)
    private String answerNickname;

    @Column(name = "answer_role_name")
    private Integer answerRoleName;

    @Column(name = "comment_time", nullable = false)
    private LocalDateTime commentTime;

    @Column(name = "deleted_time")
    private LocalDateTime deletedTime;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;
}
