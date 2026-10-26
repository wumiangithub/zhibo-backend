package com.zhibo.live.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zhibo.live.viewer.dto.ViewerUploadResult;
import com.zhibo.live.volc.ViewerTosUploader;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ViewerFileUploadServiceTest {

    private ViewerTosUploader tosUploader;
    private ViewerFileUploadService service;

    @BeforeEach
    void setUp() {
        tosUploader = mock(ViewerTosUploader.class);
        service = new ViewerFileUploadService(tosUploader);
    }

    @Test
    void upload_usesClientObjectKeyLikeDirect() throws Exception {
        String objectKey = "viewer/feedback/9_171.png";
        when(tosUploader.put(eq(objectKey), any(), anyLong(), eq("image/png")))
                .thenReturn("https://qklive-testcos.cjs99.com/" + objectKey);
        when(tosUploader.location(objectKey))
                .thenReturn("https://bucket.tos-cn-shanghai.volces.com/" + objectKey);
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.png", "image/png", new ByteArrayInputStream(new byte[] {1, 2, 3}));

        ViewerUploadResult result =
                service.upload(ViewerUploadBiz.FEEDBACK, objectKey, file, "image/png");

        assertEquals(objectKey, result.getObjectKey());
        assertEquals("https://qklive-testcos.cjs99.com/" + objectKey, result.getUrl());
        assertEquals(
                "https://bucket.tos-cn-shanghai.volces.com/" + objectKey, result.getLocation());
    }

    @Test
    void upload_rejectsObjectKeyOutsideBizPrefix() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.png", "image/png", new byte[] {1});
        ViewerAuthException ex = assertThrows(
                ViewerAuthException.class,
                () -> service.upload(
                        ViewerUploadBiz.AVATAR,
                        "viewer/feedback/9_1.png",
                        file,
                        null));
        assertEquals("对象键无效", ex.getMessage());
    }

    @Test
    void upload_rejectsEmptyObjectKey() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.png", "image/png", new byte[] {1});
        ViewerAuthException ex = assertThrows(
                ViewerAuthException.class,
                () -> service.upload(ViewerUploadBiz.AVATAR, "  ", file, null));
        assertEquals("对象键无效", ex.getMessage());
    }

    @Test
    void upload_rejectsEmptyFile() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);
        ViewerAuthException ex = assertThrows(
                ViewerAuthException.class,
                () -> service.upload(
                        ViewerUploadBiz.AVATAR, "viewer/avatar/9_1.png", file, null));
        assertEquals("请选择图片", ex.getMessage());
    }
}
