package com.zhibo.live.web;

import com.zhibo.vhall.exception.VhallException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void ok_returnsCodeZero() throws Exception {
        mockMvc.perform(get("/probe/ok").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.msg").value("ok"))
                .andExpect(jsonPath("$.data").value("pong"));
    }

    @Test
    void vhallException_mapsCodeAndMsg() throws Exception {
        mockMvc.perform(get("/probe/vhall").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(600))
                .andExpect(jsonPath("$.msg").value("参数错误"));
    }

    @Test
    void illegalArgument_mapsTo400() throws Exception {
        mockMvc.perform(get("/probe/bad").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("标题不能为空"));
    }

    @Test
    void unexpected_mapsTo500WithoutLeakingDetail() throws Exception {
        mockMvc.perform(get("/probe/boom").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("服务器错误"));
    }

    @Test
    void vhallExceptionWithoutCode_mapsTo502() throws Exception {
        mockMvc.perform(get("/probe/vhall-net").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(502))
                .andExpect(jsonPath("$.msg").value("微吼网络失败"));
    }

    @RestController
    static class ProbeController {

        @GetMapping("/probe/ok")
        public ApiResponse<String> ok() {
            return ApiResponse.ok("pong");
        }

        @GetMapping("/probe/vhall")
        public ApiResponse<Void> vhall() {
            throw new VhallException(600, "rid", "参数错误");
        }

        @GetMapping("/probe/vhall-net")
        public ApiResponse<Void> vhallNet() {
            throw new VhallException("微吼网络失败");
        }

        @GetMapping("/probe/bad")
        public ApiResponse<Void> bad() {
            throw new IllegalArgumentException("标题不能为空");
        }

        @GetMapping("/probe/boom")
        public ApiResponse<Void> boom() {
            throw new IllegalStateException("secret internals");
        }
    }
}
