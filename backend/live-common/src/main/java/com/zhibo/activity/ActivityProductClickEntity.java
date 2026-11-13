package com.zhibo.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 小黄车商品点击流水。 */
@Entity
@Table(name = "activity_product_click")
@Getter
@Setter
public class ActivityProductClickEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "activity_id", nullable = false, length = 32)
    private String activityId;

    @Column(name = "spu_id", nullable = false, length = 32)
    private String spuId;

    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "clicked_at", nullable = false)
    private LocalDateTime clickedAt;
}
