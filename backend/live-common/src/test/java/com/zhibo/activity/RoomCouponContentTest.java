package com.zhibo.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class RoomCouponContentTest {

    @Test
    void roundTrip() {
        RoomCouponContent src = new RoomCouponContent(
                10001L,
                "CPN2401",
                "满\"减\"券",
                "500.00",
                "2026-09-06",
                "2026-10-06",
                88L,
                58L,
                "事业一部");
        RoomCouponContent parsed = RoomCouponContent.parse(src.toJson());
        assertNotNull(parsed);
        assertEquals(10001L, parsed.couponId());
        assertEquals("CPN2401", parsed.couponNo());
        assertEquals("满\"减\"券", parsed.couponName());
        assertEquals("500.00", parsed.couponAmount());
        assertEquals("2026-09-06", parsed.couponStartDate());
        assertEquals("2026-10-06", parsed.couponEndDate());
        assertEquals(88L, parsed.employeeId());
        assertEquals(58L, parsed.deptId());
        assertEquals("事业一部", parsed.deptName());
    }

    @Test
    void omitDeptWhenAbsent() {
        RoomCouponContent src = new RoomCouponContent(
                1L, "N", "券", "1.00", "2026-01-01", "2026-12-31", 9L, null, null);
        String json = src.toJson();
        assertEquals(false, json.contains("deptId"));
        assertEquals(false, json.contains("deptName"));
        RoomCouponContent parsed = RoomCouponContent.parse(json);
        assertNotNull(parsed);
        assertNull(parsed.deptId());
        assertNull(parsed.deptName());
    }

    @Test
    void rejectBlankName() {
        assertNull(RoomCouponContent.parse(
                "{\"couponId\":1,\"couponName\":\"\",\"couponAmount\":\"1.00\"}"));
    }
}
