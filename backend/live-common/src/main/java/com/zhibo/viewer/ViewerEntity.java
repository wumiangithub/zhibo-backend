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

/** 观众本人与小程序登录凭证（D78 单表）。 */
@Entity
@Table(name = "viewer")
@Getter
@Setter
public class ViewerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 微信小程序 openid。本轮登录查找键。接口不回。 */
    @Column(name = "mini_openid", length = 64)
    private String miniOpenid;

    /** 微信 unionid。有则写；没带回不清空。接口不回。 */
    @Column(length = 64)
    private String unionid;

    /**
     * 首次注册渠道。{@link ViewerRegisterChannel#WECHAT} / {@link ViewerRegisterChannel#IMPORT}。
     * 只写首次，永不覆盖。接口不回。
     */
    @Column(name = "register_channel", nullable = false)
    private int registerChannel = ViewerRegisterChannel.WECHAT;

    /** 纯数字手机号，不含国家码。接口不回明文。 */
    @Column(length = 20)
    private String phone;

    /** 如 86。 */
    @Column(name = "phone_country_code", length = 8)
    private String phoneCountryCode;

    /** 资料页写入；业务上限 16 字。 */
    @Column(length = 64)
    private String nickname;

    /** 资料页头像访问地址。 */
    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    /** 头像 TOS 对象键，与 avatarUrl 成对。 */
    @Column(name = "avatar_object_key", length = 1024)
    private String avatarObjectKey;

    /** {@link ViewerStatus#NORMAL} / {@link ViewerStatus#DISABLED}。与 {@link #banAccess} 同步。 */
    @Column(nullable = false)
    private int status = ViewerStatus.NORMAL;

    /** 禁止评论：0 否 / 1 是（D82；本轮只落库，观看端细则后补）。 */
    @Column(name = "ban_comment", nullable = false)
    private int banComment = 0;

    /** 禁止购买：0 否 / 1 是（D82；本轮只落库，商城细则后补）。 */
    @Column(name = "ban_purchase", nullable = false)
    private int banPurchase = 0;

    /** 禁止访问：0 否 / 1 是；为 1 时 {@link #status}=停用，挡登录（D82）。 */
    @Column(name = "ban_access", nullable = false)
    private int banAccess = 0;

    /** 最近一次勾选协议并登录成功。 */
    @Column(name = "agreed_time", nullable = false)
    private LocalDateTime agreedTime;

    /** 最近一次登录成功。 */
    @Column(name = "last_login_time", nullable = false)
    private LocalDateTime lastLoginTime;

    /** 最近访问；可空。登录成功与 presence/ping 写入（D79）。 */
    @Column(name = "last_visit_time")
    private LocalDateTime lastVisitTime;

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time", nullable = false)
    private LocalDateTime updatedTime;
}
