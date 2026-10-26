package com.zhibo.live.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zhibo.live.viewer.dto.ViewerProfileUpdateRequest;
import com.zhibo.live.viewer.dto.ViewerUserResponse;
import com.zhibo.live.volc.ViewerTosUploader;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
        req.setAvatarUrl("https://evil.example/viewer/avatar/9_1.png");
        req.setAvatarObjectKey("viewer/avatar/9_1.png");
        ViewerAuthException ex = assertThrows(ViewerAuthException.class, () -> service.update(9L, req));
        assertEquals("头像地址无效", ex.getMessage());
    }

    @Test
    void update_avatarRequiresObjectKey() {
        ViewerProfileUpdateRequest req = new ViewerProfileUpdateRequest();
        req.setAvatarUrl("https://qklive-testcos.cjs99.com/viewer/avatar/9_1.png");
        ViewerAuthException ex = assertThrows(ViewerAuthException.class, () -> service.update(9L, req));
        assertEquals("请同时提交头像地址和对象键", ex.getMessage());
    }

    @Test
    void update_avatarStoresUrlAndObjectKey() {
        when(tosUploader.isAllowedAvatarUrl(anyString())).thenReturn(true);
        ViewerProfileUpdateRequest req = new ViewerProfileUpdateRequest();
        req.setAvatarUrl("https://qklive-testcos.cjs99.com/viewer/avatar/9_1.png");
        req.setAvatarObjectKey("viewer/avatar/9_1.png");
        ViewerUserResponse user = service.update(9L, req);
        assertEquals("viewer/avatar/9_1.png", user.getAvatarObjectKey());
        assertEquals(req.getAvatarUrl(), user.getAvatarUrl());
        assertEquals("viewer/avatar/9_1.png", viewer.getAvatarObjectKey());
    }
}
