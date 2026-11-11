package com.zhibo.live.org;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * CRM 员工档案 {@code hr_uimsyhxx}（只读；订单中心 {@code salesmanId} = {@code identifier}）。
 */
@Entity
@Table(name = "hr_uimsyhxx")
@Getter
@Setter
public class HrEmployeeEntity {

    @Id
    @Column(name = "rs_mainkey_id")
    private Long id;

    /** 员工唯一标识（UUID）；下单传给订单中心的 {@code salesmanId}。 */
    @Column(name = "identifier", nullable = false, length = 50)
    private String identifier;
}
