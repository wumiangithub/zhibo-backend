package com.zhibo.activity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 本场开播成员（主播 / 助理 / 嘉宾）。
 * {@code role} 存 {@link ActivityMemberRole#getCode()}；姓名不落本表。
 */
@Entity
@Table(name = "activity_member")
@Getter
@Setter
public class ActivityMemberEntity {

    @EmbeddedId
    private ActivityMemberId id;

    /** 开播角色：1 主播 / 2 助理 / 3 嘉宾，见 {@link ActivityMemberRole}。 */
    @Column(name = "role", nullable = false)
    private int role;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "nickname", length = 64)
    private String nickname;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "tag", length = 32)
    private String tag;
}
