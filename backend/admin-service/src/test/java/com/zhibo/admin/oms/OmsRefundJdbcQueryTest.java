package com.zhibo.admin.oms;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

@ExtendWith(MockitoExtension.class)
class OmsRefundJdbcQueryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbc;

    private OmsDataSourceProperties properties;
    private OmsRefundJdbcQuery query;

    @BeforeEach
    void setUp() {
        properties = new OmsDataSourceProperties();
        properties.setPassword("secret-for-test-only");
        query = new OmsRefundJdbcQuery(properties, jdbc);
    }

    @Test
    void isAvailable_requiresPassword() {
        assertTrue(query.isAvailable());
        properties.setPassword("");
        assertFalse(query.isAvailable());
        properties.setPassword("x");
        properties.setHost("");
        assertFalse(query.isAvailable());
    }

    @Test
    void emptyOrderNos_doesNotQuery() {
        assertTrue(query.sumCompletedRefundMoney(List.of()).isEmpty());
        assertTrue(query.sumCompletedRefundMoney(null).isEmpty());
        verify(jdbc, never()).query(any(String.class), any(SqlParameterSource.class), any(RowMapper.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void sumsPerOrder_andMergesDefensively() {
        when(jdbc.query(eq(OmsRefundJdbcQuery.SUM_COMPLETED_SQL), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(
                        new OmsRefundJdbcQuery.RefundSum("ORD-1", new BigDecimal("1.00")),
                        new OmsRefundJdbcQuery.RefundSum("ORD-1", new BigDecimal("0.50")),
                        new OmsRefundJdbcQuery.RefundSum("ORD-2", new BigDecimal("3.25"))));

        Map<String, BigDecimal> result = query.sumCompletedRefundMoney(List.of("ORD-1", "ORD-2"));

        assertEquals(new BigDecimal("1.50"), result.get("ORD-1"));
        assertEquals(new BigDecimal("3.25"), result.get("ORD-2"));

        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc).query(eq(OmsRefundJdbcQuery.SUM_COMPLETED_SQL), params.capture(), any(RowMapper.class));
        assertEquals(List.of("ORD-1", "ORD-2"), params.getValue().getValue("orderNos"));
        String sql = OmsRefundJdbcQuery.SUM_COMPLETED_SQL;
        assertTrue(sql.contains("SUM(refund_order_money)"));
        assertTrue(sql.contains("rs_status = 1"));
        assertTrue(sql.contains("refund_status = 3"));
        assertTrue(sql.contains("GROUP BY refund_order"));
        assertFalse(sql.contains("refund_order_je"));
        assertFalse(sql.contains("refund_amount"));
        assertFalse(sql.contains("uimsuserorder_refund "));
    }

    @Test
    @SuppressWarnings("unchecked")
    void batchesLargeInLists() {
        List<String> nos = new ArrayList<>();
        for (int i = 0; i < 501; i++) {
            nos.add("O" + i);
        }
        when(jdbc.query(eq(OmsRefundJdbcQuery.SUM_COMPLETED_SQL), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of());

        query.sumCompletedRefundMoney(nos);

        ArgumentCaptor<SqlParameterSource> params = ArgumentCaptor.forClass(SqlParameterSource.class);
        verify(jdbc, times(2))
                .query(eq(OmsRefundJdbcQuery.SUM_COMPLETED_SQL), params.capture(), any(RowMapper.class));
        List<String> first = (List<String>) params.getAllValues().get(0).getValue("orderNos");
        List<String> second = (List<String>) params.getAllValues().get(1).getValue("orderNos");
        assertEquals(500, first.size());
        assertEquals(1, second.size());
    }

    @Test
    void jdbcUrl_hasTimeoutsAndReadOnly_andNoPassword() {
        String url = properties.jdbcUrl();
        assertTrue(url.contains("connectTimeout=5000"));
        assertTrue(url.contains("socketTimeout=10000"));
        assertTrue(url.contains("defaultReadOnly=true"));
        assertTrue(url.contains("utf8"));
        assertTrue(url.contains("utf8mb4"));
        assertFalse(url.contains("secret"));
        assertFalse(properties.toString().contains("secret-for-test-only"));
        assertTrue(properties.toString().contains("password=***"));
    }
}
