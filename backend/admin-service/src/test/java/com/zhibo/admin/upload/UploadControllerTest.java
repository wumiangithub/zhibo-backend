package com.zhibo.admin.upload;

import com.zhibo.admin.upload.dto.UploadImageResponse;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UploadControllerTest {

    private MockMvc mockMvc;
    private UploadService uploadService;

    @BeforeEach
    void setUp() {
        uploadService = mock(UploadService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new UploadController(uploadService))
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
}
