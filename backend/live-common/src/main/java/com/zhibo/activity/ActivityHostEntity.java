package com.zhibo.activity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 活动主播。姓名不落在这张表，列表时按员工表查。 */
@Entity
@Table(name = "activity_host")
@Getter
@Setter
public class ActivityHostEntity {

    @EmbeddedId
    private ActivityHostId id;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
