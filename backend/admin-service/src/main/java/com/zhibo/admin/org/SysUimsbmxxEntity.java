package com.zhibo.admin.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** CRM 部门表 {@code sys_uimsbmxx}（只读导入；禁止 Flyway 重建）。 */
@Entity
@Table(name = "sys_uimsbmxx")
@Getter
@Setter
public class SysUimsbmxxEntity {

    @Id
    @Column(name = "rs_mainkey_id")
    private Long id;

    @Column(name = "rs_status", nullable = false)
    private Integer rsStatus;

    @Column(name = "bmbm", nullable = false, length = 50)
    private String bmbm;

    @Column(name = "bmmc", nullable = false, length = 60)
    private String bmmc;

    @Column(name = "fbmid", nullable = false)
    private Long fbmid;

    /** 启用：0 未启用 / 1 启用 */
    @Column(name = "qybz", nullable = false)
    private Integer qybz;
}
