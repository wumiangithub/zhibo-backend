package com.zhibo.admin.upload;

import com.zhibo.admin.upload.dto.UploadImageResponse;
import com.zhibo.vhall.client.VhallClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadServiceTest {

    @Mock
    private VhallClient vhallClient;

    @InjectMocks
    private UploadService uploadService;

    @Test
    void uploadImage_proxiesToVhallAndReturnsDomainUrl() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "cover.png", "image/png", new byte[]{1, 2, 3});

        when(vhallClient.postMultipartForData(
                eq(UploadService.PATH_UPLOAD),
                eq(Map.of("type", "image")),
                eq(UploadService.VHALL_FILE_FIELD),
                any(Resource.class),
                eq(Map.class)))
                .thenReturn(Map.of("domain_url", "https://cdn.vhall.com/cover.png"));

        UploadImageResponse response = uploadService.uploadImage(file);
        assertEquals("https://cdn.vhall.com/cover.png", response.getUrl());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Resource> resourceCaptor = ArgumentCaptor.forClass(Resource.class);
        verify(vhallClient).postMultipartForData(
                eq(UploadService.PATH_UPLOAD),
                eq(Map.of("type", "image")),
                eq(UploadService.VHALL_FILE_FIELD),
                resourceCaptor.capture(),
                eq(Map.class));
        assertEquals("cover.png", resourceCaptor.getValue().getFilename());
    }

    @Test
    void uploadImage_rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", "a.jpg", "image/jpeg", new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> uploadService.uploadImage(file));
    }

    @Test
    void uploadImage_rejectsBadExtension() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.webp", "image/webp", new byte[]{1});
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> uploadService.uploadImage(file));
        assertEquals("仅支持 bmp/gif/jpg/jpeg/png/psd 格式", ex.getMessage());
    }
}
