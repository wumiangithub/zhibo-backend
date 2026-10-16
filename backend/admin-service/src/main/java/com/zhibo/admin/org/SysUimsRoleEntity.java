package com.zhibo.admin.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 主系统角色表 {@code sys_uimsrole}（只读共用，禁止本站改结构）。 */
@Entity
@Table(name = "sys_uimsrole")
@Getter
@Setter
public class SysUimsRoleEntity {

    @Id
    @Column(name = "rs_mainkey_id")
    private Long id;

    @Column(name = "rs_status", nullable = false)
    private Integer rsStatus;

    @Column(name = "rs_dispindex", nullable = false)
    private Integer rsDispindex;

    @Column(name = "role_name", nullable = false, length = 50)
    private String roleName;

    @Column(length = 500)
    private String remark;
}
