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

    /**
     * 企微部门 JSON，形如
     * {@code [{"department_id":10,"department_name":"技术部","is_leader":false}]}。
     */
    @Column(name = "qw_bmxx", columnDefinition = "json")
    private String qwBmxx;

    /** 对齐旧 JWT {@code user_flag}；表列 {@code user_flg}。 */
    @Column(name = "user_flg")
    private Integer userFlg;

    /** 员工 ID（CRM）；订单中心 saler JWT 的 {@code yhid}。 */
    @Column(name = "yhid")
    private Long yhid;

    /** CRM 档案码；有则作为 saler JWT 的 {@code identifier}。 */
    @Column(length = 50)
    private String code;
}
