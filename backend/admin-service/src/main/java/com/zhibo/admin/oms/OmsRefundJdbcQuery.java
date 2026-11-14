package com.zhibo.admin.oms;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * 用独立 Hikari 池查 OMS，不注册为 Spring {@code DataSource} Bean，以免干扰本站主数据源自动配置。
 * 不映射 JPA 实体。只选订单号与金额，不查收款人等个人信息列。
 */
@Slf4j
@Repository
public class OmsRefundJdbcQuery implements OmsRefundQuery {

    static final int BATCH_SIZE = 500;

    /** 完成态明细按订单号汇总；不用 refund_order_je（同一退款多条流水会重复计算）。 */
    static final String SUM_COMPLETED_SQL =
            "SELECT refund_order, SUM(refund_order_money) AS refund_money "
                    + "FROM uimsuserorder_refund_list "
                    + "WHERE rs_status = 1 AND refund_status = 3 "
                    + "AND refund_order IN (:orderNos) "
                    + "GROUP BY refund_order";

    private final OmsDataSourceProperties properties;
    private final Object lock = new Object();
    private volatile NamedParameterJdbcTemplate jdbc;
    private volatile HikariDataSource dataSource;

    @Autowired
    public OmsRefundJdbcQuery(OmsDataSourceProperties properties) {
        this.properties = properties;
    }

    /** 单测注入，不建真实连接池。 */
    OmsRefundJdbcQuery(OmsDataSourceProperties properties, NamedParameterJdbcTemplate jdbc) {
        this.properties = properties;
        this.jdbc = jdbc;
    }

    @Override
    public boolean isAvailable() {
        return properties != null && properties.isConfigured();
    }

    @Override
    public Map<String, BigDecimal> sumCompletedRefundMoney(Collection<String> orderNos) {
        List<String> distinct = distinctOrderNos(orderNos);
        if (distinct.isEmpty()) {
            return Map.of();
        }
        NamedParameterJdbcTemplate template = requireTemplate();
        Map<String, BigDecimal> result = new HashMap<>();
        for (int i = 0; i < distinct.size(); i += BATCH_SIZE) {
            List<String> batch = distinct.subList(i, Math.min(i + BATCH_SIZE, distinct.size()));
            MapSqlParameterSource params = new MapSqlParameterSource("orderNos", batch);
            List<RefundSum> rows = template.query(
                    SUM_COMPLETED_SQL, params, (rs, rowNum) -> new RefundSum(
                            rs.getString("refund_order"), rs.getBigDecimal("refund_money")));
            for (RefundSum row : rows) {
                if (row.orderNo() == null || row.orderNo().isBlank() || row.money() == null) {
                    continue;
                }
                result.merge(row.orderNo(), row.money(), BigDecimal::add);
            }
        }
        return result;
    }

    private NamedParameterJdbcTemplate requireTemplate() {
        NamedParameterJdbcTemplate existing = jdbc;
        if (existing != null) {
            return existing;
        }
        if (!isAvailable()) {
            throw new IllegalStateException("OMS 数据源未配置，不能查询退款明细");
        }
        synchronized (lock) {
            if (jdbc != null) {
                return jdbc;
            }
            HikariConfig config = new HikariConfig();
            config.setPoolName("oms-readonly");
            config.setJdbcUrl(properties.jdbcUrl());
            config.setUsername(properties.getUsername());
            config.setPassword(properties.getPassword());
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
            config.setReadOnly(true);
            config.setMaximumPoolSize(5);
            config.setMinimumIdle(0);
            config.setConnectionTimeout(5_000);
            config.setInitializationFailTimeout(-1);
            config.setIdleTimeout(60_000);
            config.setMaxLifetime(300_000);
            config.setAutoCommit(true);
            HikariDataSource ds = new HikariDataSource(config);
            this.dataSource = ds;
            this.jdbc = new NamedParameterJdbcTemplate(ds);
            log.info("已创建 OMS 只读连接池，host={}, database={}", properties.getHost(), properties.getDatabase());
            return this.jdbc;
        }
    }

    @PreDestroy
    void close() {
        HikariDataSource ds = dataSource;
        if (ds != null && !ds.isClosed()) {
            ds.close();
        }
    }

    static List<String> distinctOrderNos(Collection<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            return List.of();
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String no : orderNos) {
            if (no == null) {
                continue;
            }
            String trimmed = no.trim();
            if (!trimmed.isEmpty()) {
                seen.add(trimmed);
            }
        }
        return new ArrayList<>(seen);
    }

    record RefundSum(String orderNo, BigDecimal money) {}
}
