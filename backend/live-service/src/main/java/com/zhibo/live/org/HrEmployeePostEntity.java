package com.zhibo.live.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** CRM 员工岗位 {@code hr_uimsyhgwxx}（只读；取 bmid 作订单中心 salesmanDeptId）。 */
@Entity
@Table(name = "hr_uimsyhgwxx")
@Getter
@Setter
public class HrEmployeePostEntity {

    @Id
    @Column(name = "rs_mainkey_id")
    private Long id;

    @Column(name = "rs_status", nullable = false)
    private Integer rsStatus;

    @Column(name = "yhid", nullable = false)
    private Long yhid;

    @Column(name = "bmid", nullable = false)
    private Long bmid;
}
