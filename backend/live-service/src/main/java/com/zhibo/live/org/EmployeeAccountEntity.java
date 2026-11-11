package com.zhibo.live.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 员工账号 {@code sys_useraccount}（只读；仅映射业务员解析所需列）。
 */
@Entity
@Table(name = "sys_useraccount")
@Getter
@Setter
public class EmployeeAccountEntity {

    @Id
    @Column(name = "rs_mainkey_id")
    private Long id;

    /** CRM 员工档案 id，对应 {@code hr_uimsyhxx.rs_mainkey_id}。 */
    @Column(name = "yhid")
    private Long yhid;
}
