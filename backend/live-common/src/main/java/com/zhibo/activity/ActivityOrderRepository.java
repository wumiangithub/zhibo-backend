package com.zhibo.activity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
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
}
