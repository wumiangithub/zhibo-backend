package com.zhibo.admin.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 员工表 {@code sys_useraccount}（已导入；只映射登录所需列，禁止 Flyway 重建 / INSERT 注册）。
 */
@Entity
@Table(name = "sys_useraccount")
@Getter
@Setter
public class SysUserAccountEntity {

    @Id
    @Column(name = "rs_mainkey_id")
    private Long id;

    @Column(name = "rs_status", nullable = false)
    private Integer rsStatus;

    @Column(name = "user_name", nullable = false, length = 50)
    private String userName;

    @Column(name = "user_account", nullable = false, length = 50)
    private String userAccount;

    @Column(nullable = false, length = 200)
    private String password;

    @Column(length = 50)
    private String openid;

    @Column(length = 50)
    private String unionid;

    @Column(length = 50)
    private String nickname;

    @Column(length = 500)
    private String headimgurl;

    @Column(name = "user_avatar", length = 500)
    private String userAvatar;
}
