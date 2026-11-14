package com.zhibo.activity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityOrderRepository extends JpaRepository<ActivityOrderEntity, Long> {

    Optional<ActivityOrderEntity> findByOrderNo(String orderNo);

    @Query("SELECT COALESCE(SUM(o.payAmount), 0) FROM ActivityOrderEntity o "
            + "WHERE o.orderStatus IN :statuses AND o.payTime >= :start AND o.payTime < :end")
    BigDecimal sumPayAmountByStatusesAndPayTimeBetween(
            @Param("statuses") Collection<Integer> statuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(o) FROM ActivityOrderEntity o "
            + "WHERE o.orderStatus IN :statuses AND o.payTime >= :start AND o.payTime < :end")
    long countByStatusesAndPayTimeBetween(
            @Param("statuses") Collection<Integer> statuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT o.username) FROM ActivityOrderEntity o "
            + "WHERE o.orderStatus IN :statuses")
    long countDistinctUsernameByStatuses(@Param("statuses") Collection<Integer> statuses);

    @Query("SELECT COALESCE(SUM(o.payAmount), 0) FROM ActivityOrderEntity o "
            + "WHERE o.orderStatus IN :statuses")
    BigDecimal sumPayAmountByStatuses(@Param("statuses") Collection<Integer> statuses);

    /** 按商品聚合本场已支付订单：productId / 去重 username / pay_amount 求和。product_id 空的行不计入。 */
    @Query("SELECT o.productId, COUNT(DISTINCT o.username), COALESCE(SUM(o.payAmount), 0) "
            + "FROM ActivityOrderEntity o "
            + "WHERE o.activityId = :activityId AND o.orderStatus IN :statuses AND o.productId IS NOT NULL "
            + "GROUP BY o.productId")
    List<Object[]> aggregatePaidByProduct(
            @Param("activityId") String activityId,
            @Param("statuses") Collection<Integer> statuses);

    /**
     * 本场全部订单的订单号与商品，用于对接 OMS 成功退款金额。不限订单状态（已全额退款也要计入）。
     * {@code product_id} 为空的行无法对上带货表的 spu，调用方可跳过。
     */
    @Query("SELECT o.orderNo, o.productId FROM ActivityOrderEntity o "
            + "WHERE o.activityId = :activityId AND o.orderNo IS NOT NULL")
    List<Object[]> findOrderNoAndProductIdByActivityId(@Param("activityId") String activityId);
}
