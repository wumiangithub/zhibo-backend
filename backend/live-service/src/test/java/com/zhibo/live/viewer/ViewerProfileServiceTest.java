package com.zhibo.live.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.live.viewer.dto.ViewerProfileUpdateRequest;
import com.zhibo.live.viewer.dto.ViewerUserResponse;
import com.zhibo.live.volc.ViewerTosUploader;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerStatus;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ViewerProfileServiceTest {

    private ViewerRepository viewerRepository;
    private ViewerTosUploader tosUploader;
    private ViewerProfileService service;
    private ViewerEntity viewer;

    @BeforeEach
    void setUp() {
        viewerRepository = mock(ViewerRepository.class);
        tosUploader = mock(ViewerTosUploader.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-26T02:00:00Z"), ZoneId.of("Asia/Shanghai"));
        service = new ViewerProfileService(viewerRepository, tosUploader, clock);

        viewer = new ViewerEntity();
        viewer.setId(9L);
        viewer.setStatus(ViewerStatus.NORMAL);
        viewer.setAgreedTime(LocalDateTime.now(clock));
        viewer.setLastLoginTime(LocalDateTime.now(clock));
        when(viewerRepository.findById(9L)).thenReturn(Optional.of(viewer));
        when(viewerRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void update_nickname() {
        ViewerProfileUpdateRequest req = new ViewerProfileUpdateRequest();
        req.setNickname(" 钱坤用户 ");
        ViewerUserResponse user = service.update(9L, req);
        assertEquals("钱坤用户", user.getNickname());
        assertEquals("钱坤用户", viewer.getNickname());
    }

    @Test
    void update_rejectsEmptyNickname() {
        ViewerProfileUpdateRequest req = new ViewerProfileUpdateRequest();
        req.setNickname("  ");
        ViewerAuthException ex = assertThrows(ViewerAuthException.class, () -> service.update(9L, req));
        assertEquals(400, ex.getCode());
        assertEquals("请输入昵称", ex.getMessage());
    }

    @Test
    void update_rejectsTooLongNickname() {
        ViewerProfileUpdateRequest req = new ViewerProfileUpdateRequest();
        req.setNickname("一二三四五六七八九十一二三四五六七");
        ViewerAuthException ex = assertThrows(ViewerAuthException.class, () -> service.update(9L, req));
        assertEquals("昵称最多16字", ex.getMessage());
    }

    @Test
    void update_avatarUrlMustBeAllowed() {
        when(tosUploader.isAllowedAvatarUrl(anyString())).thenReturn(false);
        ViewerProfileUpdateRequest req = new ViewerProfileUpdateRequest();
        req.setAvatarUrl("https://evil.example/a.jpg");
        ViewerAuthException ex = assertThrows(ViewerAuthException.class, () -> service.update(9L, req));
        assertEquals("头像地址无效", ex.getMessage());
    }

    @Test
    void uploadAvatar_writesUrl() throws Exception {
        when(tosUploader.put(anyString(), any(), anyLong(), eq("image/png")))
                .thenReturn("https://qklive-testcos.cjs99.com/viewer/avatar/9_1.png");
        MockMultipartFile file = new MockMultipartFile(
                "file", "a.png", "image/png", new ByteArrayInputStream(new byte[] {1, 2, 3}));
        ViewerUserResponse user = service.uploadAvatar(9L, file);
        assertEquals("https://qklive-testcos.cjs99.com/viewer/avatar/9_1.png", user.getAvatarUrl());
        assertTrue(viewer.getAvatarUrl().contains("viewer/avatar/"));
        verify(tosUploader).put(anyString(), any(), eq(3L), eq("image/png"));
    }

    @Test
    void uploadAvatar_rejectsEmpty() {
        MockMultipartFile file = new MockMultipartFile("file", new byte[0]);
        ViewerAuthException ex = assertThrows(ViewerAuthException.class, () -> service.uploadAvatar(9L, file));
        assertEquals("请选择头像", ex.getMessage());
    }
}
