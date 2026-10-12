package com.zhibo.admin.upload;

import com.zhibo.admin.upload.dto.TosStsResponse;
import com.zhibo.admin.upload.dto.UploadImageResponse;
import com.zhibo.admin.volc.VolcStsException;
import com.zhibo.admin.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadControllerTest {

    private MockMvc mockMvc;
    private UploadService uploadService;
    private TosStsService tosStsService;

    @BeforeEach
    void setUp() {
        uploadService = mock(UploadService.class);
        tosStsService = mock(TosStsService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UploadController(uploadService, tosStsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void uploadImage_returnsUrl() throws Exception {
        when(uploadService.uploadImage(any()))
                .thenReturn(new UploadImageResponse("https://cdn.example/x.jpg"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "x.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1, 2});

        mockMvc.perform(multipart("/api/uploads/image").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.url").value("https://cdn.example/x.jpg"));
    }

    @Test
    void uploadImage_validationFailure_returns400() throws Exception {
        when(uploadService.uploadImage(any()))
                .thenThrow(new IllegalArgumentException("请选择要上传的图片"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "x.jpg", MediaType.IMAGE_JPEG_VALUE, new byte[]{1});

        mockMvc.perform(multipart("/api/uploads/image").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("请选择要上传的图片"));
    }

    @Test
    void tosSts_returnsCredentials() throws Exception {
        when(tosStsService.issueCredentials()).thenReturn(new TosStsResponse(
                "cn-shanghai",
                "tos-cn-shanghai.volces.com",
                "qky-live-sucai-test",
                "tmp-ak",
                "tmp-sk",
                "tmp-token",
                "2026-10-10T06:00:00Z"));

        mockMvc.perform(get("/api/uploads/tos-sts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.region").value("cn-shanghai"))
                .andExpect(jsonPath("$.data.endpoint").value("tos-cn-shanghai.volces.com"))
                .andExpect(jsonPath("$.data.bucketName").value("qky-live-sucai-test"))
                .andExpect(jsonPath("$.data.accessKeyId").value("tmp-ak"))
                .andExpect(jsonPath("$.data.sessionToken").value("tmp-token"))
                .andExpect(jsonPath("$.data.expiredTime").value("2026-10-10T06:00:00Z"));
    }

    @Test
    void tosSts_missingConfig_returns400() throws Exception {
        when(tosStsService.issueCredentials())
                .thenThrow(new IllegalArgumentException("未配置火山 TOS 凭证"));

        mockMvc.perform(get("/api/uploads/tos-sts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("未配置火山 TOS 凭证"));
    }

    @Test
    void tosSts_stsFailure_returns502() throws Exception {
        when(tosStsService.issueCredentials())
                .thenThrow(new VolcStsException(502, "火山 STS 调用失败"));

        mockMvc.perform(get("/api/uploads/tos-sts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(502))
                .andExpect(jsonPath("$.msg").value("火山 STS 调用失败"));
    }
}
