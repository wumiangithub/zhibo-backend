package com.zhibo.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TradeTrendCalculatorTest {

    @Test
    void aggregatesAmountAndSoldIntoBuckets() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 22, 11, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 22, 11, 3, 0);
        List<Object[]> rows = List.of(
                new Object[] {LocalDateTime.of(2026, 10, 22, 11, 0, 30), new BigDecimal("10.00")},
                new Object[] {LocalDateTime.of(2026, 10, 22, 11, 0, 45), new BigDecimal("20.50")},
                new Object[] {LocalDateTime.of(2026, 10, 22, 11, 2, 10), new BigDecimal("5.00")});

        List<TradeTrendPoint> points = TradeTrendCalculator.aggregate(rows, start, end, 60);

        assertEquals(3, points.size());
        assertEquals("2026-10-22 11:00:00", points.get(0).time());
        assertEquals(new BigDecimal("30.50"), points.get(0).dealAmount());
        assertEquals(2, points.get(0).soldCount());
        assertEquals(0, points.get(1).soldCount());
        assertEquals(BigDecimal.ZERO, points.get(1).dealAmount());
        assertEquals(1, points.get(2).soldCount());
        assertEquals(new BigDecimal("5.00"), points.get(2).dealAmount());
    }

    @Test
    void emptyRangeReturnsNoPoints() {
        LocalDateTime t = LocalDateTime.of(2026, 10, 22, 11, 0, 0);
        assertEquals(List.of(), TradeTrendCalculator.aggregate(List.of(), t, t, 60));
    }
}
