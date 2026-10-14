package com.zhibo.activity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** 活动关联商品。名称是绑定时的快照。 */
@Entity
@Table(name = "activity_product")
@Getter
@Setter
public class ActivityProductEntity {

    @EmbeddedId
    private ActivityProductId id;

    @Column(name = "product_name", nullable = false, length = 128)
    private String productName;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
