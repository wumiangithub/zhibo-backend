package com.zhibo.admin.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 主系统员工账号-角色关联 {@code sys_uimsyhid_role}。{@code account_id} = 账号主键。 */
@Entity
@Table(name = "sys_uimsyhid_role")
@Getter
@Setter
public class SysUimsYhidRoleEntity {

    @Id
    @Column(name = "rs_mainkey_id")
    private Long id;

    @Column(name = "rs_status", nullable = false)
    private Integer rsStatus;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "role_id", nullable = false)
    private Long roleId;
}
