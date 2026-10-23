package com.zhibo.viewer;

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

/** 观众本人。手机号在这张表上，openid 在 {@link ViewerIdentityEntity}。 */
@Entity
@Table(name = "viewer")
@Getter
@Setter
public class ViewerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 纯数字手机号，不含国家码。接口不回明文。 */
    @Column(length = 20)
    private String phone;

    /** 如 86。 */
    @Column(name = "phone_country_code", length = 8)
    private String phoneCountryCode;

    /** 本轮登录不写。 */
    @Column(length = 64)
    private String nickname;

    /** 本轮登录不写。 */
    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    /** {@link ViewerStatus#NORMAL} / {@link ViewerStatus#DISABLED}。 */
    @Column(nullable = false)
    private int status = ViewerStatus.NORMAL;

    /** 最近一次勾选协议并登录成功。 */
    @Column(name = "agreed_time", nullable = false)
    private LocalDateTime agreedTime;

    /** 最近一次登录成功。 */
    @Column(name = "last_login_time", nullable = false)
    private LocalDateTime lastLoginTime;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;
}
