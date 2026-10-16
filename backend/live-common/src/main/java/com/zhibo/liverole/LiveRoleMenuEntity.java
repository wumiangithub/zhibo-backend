package com.zhibo.liverole;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 本站角色菜单关联 {@code live_role_menu}。角色来自主系统 {@code sys_uimsrole}。 */
@Entity
@Table(name = "live_role_menu")
@Getter
@Setter
public class LiveRoleMenuEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "rs_mainkey_id")
    private Long id;

    @Column(name = "rs_status", nullable = false)
    private Integer rsStatus;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "menu_code", nullable = false, length = 64)
    private String menuCode;

    @Column(name = "updated_by")
    private Long updatedBy;
}
