package com.zhibo.vhall.dto;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.vhall.exception.VhallException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VhallResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void deserialize_mapsRequestIdAndData() throws Exception {
        String json = """
                {
                  "code": 200,
                  "msg": "success",
                  "data": { "webinar_id": 123 },
                  "request_id": "req-abc"
                }
                """;

        VhallResponse<Map<String, Object>> response = objectMapper.readValue(
                json, new TypeReference<>() {
                });

        assertTrue(response.isSuccess());
        assertEquals("req-abc", response.getRequestId());
        assertEquals(123, ((Number) response.requireData().get("webinar_id")).intValue());
    }

    @Test
    void requireData_throwsVhallExceptionWhenNotSuccess() {
        VhallResponse<Void> response = new VhallResponse<>();
        response.setCode(600);
        response.setMsg("参数错误");
        response.setRequestId("req-err");

        assertFalse(response.isSuccess());
        VhallException ex = assertThrows(VhallException.class, response::requireData);
        assertEquals(600, ex.getCode());
        assertEquals("req-err", ex.getRequestId());
        assertEquals("参数错误", ex.getMessage());
    }

    @Test
    void of_usesDefaultMessageWhenMsgBlank() {
        VhallResponse<Void> response = new VhallResponse<>();
        response.setCode(510009);
        response.setMsg("  ");

        VhallException ex = VhallException.of(response);
        assertEquals(510009, ex.getCode());
        assertTrue(ex.getMessage().contains("510009"));
    }
}
