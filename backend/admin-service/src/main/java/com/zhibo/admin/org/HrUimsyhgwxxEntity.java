package com.zhibo.admin.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** CRM 员工岗位 {@code hr_uimsyhgwxx}（只读导入；禁止 Flyway 重建）。 */
@Entity
@Table(name = "hr_uimsyhgwxx")
@Getter
@Setter
public class HrUimsyhgwxxEntity {

    @Id
    @Column(name = "rs_mainkey_id")
    private Long id;

    @Column(name = "rs_status", nullable = false)
    private Integer rsStatus;

    /** 员工档案 ID，关联账号 yhid */
    @Column(name = "yhid", nullable = false)
    private Long yhid;

    /** 部门 ID */
    @Column(name = "bmid", nullable = false)
    private Long bmid;
}
