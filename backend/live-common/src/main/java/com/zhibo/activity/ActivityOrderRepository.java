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

    /** 按商品统计本场已付订单行数（中控台商品「已售」；一单一品）。 */
    @Query("SELECT o.productId, COUNT(o) FROM ActivityOrderEntity o "
            + "WHERE o.activityId = :activityId AND o.orderStatus IN :statuses AND o.productId IS NOT NULL "
            + "GROUP BY o.productId")
    List<Object[]> countPaidOrdersByProduct(
            @Param("activityId") String activityId,
            @Param("statuses") Collection<Integer> statuses);

    /**
     * 本场全部订单的订单号与商品，用于对接 OMS 成功退款金额。不限订单状态（已全额退款也要计入）。
     * {@code product_id} 为空的行无法对上带货表的 spu，调用方可跳过。
     */
    @Query("SELECT o.orderNo, o.productId FROM ActivityOrderEntity o "
            + "WHERE o.activityId = :activityId AND o.orderNo IS NOT NULL")
    List<Object[]> findOrderNoAndProductIdByActivityId(@Param("activityId") String activityId);

    /** 本场按用户×状态聚合订单笔数（观众明细：待支付 / 本场购买）。 */
    @Query("SELECT o.username, o.orderStatus, COUNT(o) FROM ActivityOrderEntity o "
            + "WHERE o.activityId = :activityId AND o.username IN :usernames "
            + "GROUP BY o.username, o.orderStatus")
    List<Object[]> countByActivityUsernameAndStatus(
            @Param("activityId") String activityId, @Param("usernames") Collection<String> usernames);

    /** 全活动已付：按用户聚合笔数与金额（观众明细累计购买）。 */
    @Query("SELECT o.username, COUNT(o), COALESCE(SUM(o.payAmount), 0) FROM ActivityOrderEntity o "
            + "WHERE o.username IN :usernames AND o.orderStatus IN :statuses GROUP BY o.username")
    List<Object[]> aggregatePaidByUsernames(
            @Param("usernames") Collection<String> usernames,
            @Param("statuses") Collection<Integer> statuses);

    /** 本场已付成交金额（中控台观看数据）。空金额按 0。 */
    @Query("SELECT COALESCE(SUM(o.payAmount), 0) FROM ActivityOrderEntity o "
            + "WHERE o.activityId = :activityId AND o.orderStatus IN :statuses")
    BigDecimal sumPayAmountByActivityAndStatuses(
            @Param("activityId") String activityId, @Param("statuses") Collection<Integer> statuses);

    /** 本场已付成交人数（username 去重）。 */
    @Query("SELECT COUNT(DISTINCT o.username) FROM ActivityOrderEntity o "
            + "WHERE o.activityId = :activityId AND o.orderStatus IN :statuses")
    long countDistinctUsernameByActivityAndStatuses(
            @Param("activityId") String activityId, @Param("statuses") Collection<Integer> statuses);

    /** 本场已付成交商品数（订单行数；一单一品）。 */
    @Query("SELECT COUNT(o) FROM ActivityOrderEntity o "
            + "WHERE o.activityId = :activityId AND o.orderStatus IN :statuses")
    long countByActivityAndStatuses(
            @Param("activityId") String activityId, @Param("statuses") Collection<Integer> statuses);

    /**
     * 本场已付订单的支付时间与金额，供中控台成交趋势按桶聚合。
     * {@code pay_time} 为空的行跳过（调用方过滤）；区间为 {@code [start, end)}。
     */
    @Query("SELECT o.payTime, o.payAmount FROM ActivityOrderEntity o "
            + "WHERE o.activityId = :activityId AND o.orderStatus IN :statuses "
            + "AND o.payTime IS NOT NULL AND o.payTime >= :start AND o.payTime < :end "
            + "ORDER BY o.payTime ASC")
    List<Object[]> findPaidPayTimeAndAmount(
            @Param("activityId") String activityId,
            @Param("statuses") Collection<Integer> statuses,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);

    /** 本场有商品的订单（中控台带货明细行源）；{@code product_id} 空的不入表。 */
    @Query("SELECT o FROM ActivityOrderEntity o "
            + "WHERE o.activityId = :activityId AND o.productId IS NOT NULL "
            + "AND o.username IS NOT NULL AND o.username <> ''")
    List<ActivityOrderEntity> findByActivityIdWithProduct(@Param("activityId") String activityId);
}
