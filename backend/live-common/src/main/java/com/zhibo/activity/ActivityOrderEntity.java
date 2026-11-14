package com.zhibo.activity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** 本站订单表，订单中心回调写入（见 zhibo-shared/docs/订单落库.md）。 */
@Entity
@Table(name = "activity_order")
@Getter
@Setter
public class ActivityOrderEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_no", nullable = false, length = 64)
    private String orderNo;

    /** 下单人用户名，对齐 viewer.username / 订单中心 third_user_id（D118）。 */
    @Column(name = "username", nullable = false, length = 64)
    private String username;

    @Column(name = "user_nickname", length = 50)
    private String userNickname;

    @Column(name = "product_id")
    private Long productId;

    @Column(name = "product_name", length = 200)
    private String productName;

    @Column(name = "product_type", length = 50)
    private String productType;

    @Column(name = "activity_id", length = 32)
    private String activityId;

    @Column(name = "salesman_id", length = 64)
    private String salesmanId;

    @Column(name = "channel")
    private Integer channel;

    @Column(name = "order_time")
    private LocalDateTime orderTime;

    @Column(name = "pay_time")
    private LocalDateTime payTime;

    @Column(name = "pay_amount", precision = 12, scale = 2)
    private BigDecimal payAmount;

    /** OMS 状态码原样落库（1010+），见 {@link ActivityOrderStatuses}。 */
    /** 库列为 SMALLINT，须声明以免 validate 期望 INTEGER。 */
    @Column(name = "order_status", nullable = false, columnDefinition = "SMALLINT")
    private Integer orderStatus = ActivityOrderStatuses.PAID;

    @CreationTimestamp
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @UpdateTimestamp
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;
}
