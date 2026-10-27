package com.zhibo.live.viewer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.live.viewer.dto.PhoneLoginRequest;
import com.zhibo.live.viewer.dto.ViewerAuthResponse;
import com.zhibo.live.viewer.dto.ViewerRefreshRequest;
import com.zhibo.live.viewer.dto.WechatLoginRequest;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRegisterChannel;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class ViewerAuthServiceTest {

    private MutableClock clock;
    private ViewerAuthProperties properties;
    private ViewerRepository viewerRepository;
    private WechatMpClient wechatMpClient;
    private ViewerJwtTokenService jwtTokenService;
    private ViewerAuthService service;
    private Store store;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-23T02:00:00Z"));
        properties = new ViewerAuthProperties();
        properties.setJwtSecret("viewer-test-secret-0123456789abcdef");
        properties.setJwtExpireSeconds(7200);
        properties.setJwtRefreshExpireSeconds(604800);
        properties.setJwtIssuer("zhibo-viewer");
        properties.setJwtAudience("viewer");
        properties.setJwtRefreshAudience("viewer-refresh");
        properties.getWechat().setMpAppId("wx8ba0027c2c9e839c");
        properties.getWechat().setMpAppSecret("test-mp-secret");

        viewerRepository = mock(ViewerRepository.class);
        wechatMpClient = mock(WechatMpClient.class);
        jwtTokenService = new ViewerJwtTokenService(properties, clock);
        service = new ViewerAuthService(
                viewerRepository,
                wechatMpClient,
                jwtTokenService,
                properties,
                clock);
        store = new Store();
        stubStore();
    }

    @Test
    void notAgreed_returns400() {
        WechatLoginRequest wechat = new WechatLoginRequest();
        wechat.setCode("c1");
        wechat.setAgreed(false);
        ViewerAuthException wechatEx = assertThrows(ViewerAuthException.class, () -> service.wechatLogin(wechat));
        assertEquals(400, wechatEx.getCode());
        assertEquals("请先阅读并同意用户协议和隐私协议", wechatEx.getMessage());

        PhoneLoginRequest phone = new PhoneLoginRequest();
        phone.setLoginCode("login");
        phone.setPhoneCode("phone");
        phone.setAgreed(false);
        ViewerAuthException phoneEx = assertThrows(ViewerAuthException.class, () -> service.phoneLogin(phone));
        assertEquals(400, phoneEx.getCode());
        assertEquals("请先阅读并同意用户协议和隐私协议", phoneEx.getMessage());
        verifyNoInteractions(wechatMpClient);
    }

    @Test
    void wechatLogin_sameOpenidKeepsViewerAndOmitsPhone() throws Exception {
        when(wechatMpClient.code2Session("c1"))
                .thenReturn(new WechatMpClient.WechatSession("oid-1", "union-1"));
        when(wechatMpClient.code2Session("c2"))
                .thenReturn(new WechatMpClient.WechatSession("oid-1", null));

        ViewerAuthResponse first = service.wechatLogin(wechat("c1"));
        ViewerAuthResponse second = service.wechatLogin(wechat("c2"));

        assertEquals(first.getUser().getId(), second.getUser().getId());
        assertEquals(1L, second.getUser().getId());
        assertFalse(second.getUser().isHasPhone());
        assertNull(second.getUser().getPhoneMasked());
        assertEquals("oid-1", store.viewer.getMiniOpenid());
        assertEquals("union-1", store.viewer.getUnionid());
        assertEquals(ViewerRegisterChannel.WECHAT, store.viewer.getRegisterChannel());
        assertNull(store.viewer.getPhone());

        String json = new ObjectMapper().writeValueAsString(second.getUser());
        assertTrue(json.contains("\"hasPhone\":false"));
        assertTrue(json.contains("\"id\":1"));
        assertFalse(json.contains("phoneMasked"));
        assertFalse(json.contains("nickname"));
    }

    @Test
    void phoneLogin_masksElevenDigitPhone() throws Exception {
        when(wechatMpClient.code2Session("login-1"))
                .thenReturn(new WechatMpClient.WechatSession("oid-phone", null));
        when(wechatMpClient.getPhoneNumber("phone-1"))
                .thenReturn(new WechatMpClient.WechatPhone("13612348079", "86"));

        PhoneLoginRequest request = new PhoneLoginRequest();
        request.setLoginCode("login-1");
        request.setPhoneCode("phone-1");
        request.setAgreed(true);
        ViewerAuthResponse response = service.phoneLogin(request);

        assertTrue(response.getUser().isHasPhone());
        assertEquals("136****8079", response.getUser().getPhoneMasked());
        assertEquals("86", store.viewer.getPhoneCountryCode());
        assertEquals("13612348079", store.viewer.getPhone());
        assertEquals(ViewerRegisterChannel.WECHAT, store.viewer.getRegisterChannel());

        String json = new ObjectMapper().writeValueAsString(response);
        assertTrue(json.contains("136****8079"));
        assertFalse(json.contains("13612348079"));
    }

    @Test
    void phoneLogin_rejectsPhoneOwnedByAnotherViewer() {
        ViewerEntity self = viewer(1L, ViewerStatus.NORMAL, null, "oid-self");
        LocalDateTime original = LocalDateTime.of(2020, 1, 1, 0, 0);
        self.setLastLoginTime(original);
        store.viewer = self;
        ViewerEntity other = viewer(2L, ViewerStatus.DISABLED, "13612348079", "oid-other");
        store.other = other;
        when(wechatMpClient.code2Session("login-1"))
                .thenReturn(new WechatMpClient.WechatSession("oid-self", null));
        when(wechatMpClient.getPhoneNumber("phone-1"))
                .thenReturn(new WechatMpClient.WechatPhone("13612348079", "86"));

        PhoneLoginRequest request = new PhoneLoginRequest();
        request.setLoginCode("login-1");
        request.setPhoneCode("phone-1");
        request.setAgreed(true);
        ViewerAuthException ex = assertThrows(ViewerAuthException.class, () -> service.phoneLogin(request));

        assertEquals(400, ex.getCode());
        assertEquals("该手机号已绑定其他微信", ex.getMessage());
        assertNull(store.viewer.getPhone());
        assertEquals(original, store.viewer.getLastLoginTime());
        assertEquals("13612348079", store.other.getPhone());
    }

    @Test
    void disabledViewer_returns403OnLoginMeAndRefresh() {
        ViewerEntity viewer = viewer(5L, ViewerStatus.DISABLED, null, "oid-off");
        LocalDateTime original = LocalDateTime.of(2020, 1, 1, 0, 0);
        viewer.setLastLoginTime(original);
        store.viewer = viewer;
        when(wechatMpClient.code2Session("c-off"))
                .thenReturn(new WechatMpClient.WechatSession("oid-off", null));

        ViewerAuthException login = assertThrows(ViewerAuthException.class, () -> service.wechatLogin(wechat("c-off")));
        assertEquals(403, login.getCode());
        assertEquals("账号已停用", login.getMessage());
        assertEquals(original, store.viewer.getLastLoginTime());

        ViewerAuthException me = assertThrows(ViewerAuthException.class, () -> service.userInfo(5L));
        assertEquals(403, me.getCode());
        assertEquals("账号已停用", me.getMessage());

        ViewerRefreshRequest refresh = new ViewerRefreshRequest();
        refresh.setAccessToken(jwtTokenService.issueAccess(5L));
        refresh.setRefreshToken(jwtTokenService.issueRefresh(5L));
        ViewerAuthException refreshed = assertThrows(ViewerAuthException.class, () -> service.refresh(refresh));
        assertEquals(403, refreshed.getCode());
        assertEquals("账号已停用", refreshed.getMessage());
    }

    @Test
    void refresh_expiredAccessWithValidRefresh_issuesNewPair() {
        store.viewer = viewer(1L, ViewerStatus.NORMAL, "13612348079", "oid-1");
        String access = jwtTokenService.issueAccess(1L);
        String refreshToken = jwtTokenService.issueRefresh(1L);
        clock.advanceSeconds(7201);

        assertThrows(ViewerAuthException.class, () -> jwtTokenService.parseUserId(access));

        ViewerRefreshRequest request = new ViewerRefreshRequest();
        request.setAccessToken(access);
        request.setRefreshToken(refreshToken);
        ViewerAuthResponse response = service.refresh(request);

        assertEquals(1L, response.getUser().getId());
        assertEquals("136****8079", response.getUser().getPhoneMasked());
        assertEquals(1L, jwtTokenService.parseUserId(response.getAccessToken()));
        assertEquals(1L, jwtTokenService.parseRefreshUserId(refreshToken));
        assertTrue(!access.equals(response.getAccessToken()));
    }

    @Test
    void refresh_rejectsDifferentSubjects() {
        ViewerRefreshRequest request = new ViewerRefreshRequest();
        request.setAccessToken(jwtTokenService.issueAccess(1L));
        request.setRefreshToken(jwtTokenService.issueRefresh(2L));

        ViewerAuthException ex = assertThrows(ViewerAuthException.class, () -> service.refresh(request));
        assertEquals(401, ex.getCode());
        assertEquals("登录状态已失效，请重新登录", ex.getMessage());
    }

    @Test
    void missingSecret_returns500WithoutNamingIt() {
        properties.setJwtSecret(" ");
        ViewerAuthException ex = assertThrows(ViewerAuthException.class, () -> service.wechatLogin(wechat("c1")));
        assertEquals(500, ex.getCode());
        assertEquals("观众登录暂不可用", ex.getMessage());
        assertFalse(ex.getMessage().contains("SECRET"));
        assertFalse(ex.getMessage().contains("VIEWER"));
        verifyNoInteractions(wechatMpClient);
    }

    private void stubStore() {
        when(viewerRepository.save(ArgumentMatchers.any(ViewerEntity.class))).thenAnswer(invocation -> {
            ViewerEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                entity.setId(store.nextViewerId++);
            }
            if (store.other != null && entity.getId().equals(store.other.getId())) {
                store.other = entity;
            } else {
                store.viewer = entity;
            }
            return entity;
        });
        when(viewerRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            if (store.viewer != null && id.equals(store.viewer.getId())) {
                return Optional.of(store.viewer);
            }
            if (store.other != null && id.equals(store.other.getId())) {
                return Optional.of(store.other);
            }
            return Optional.empty();
        });
        when(viewerRepository.findByMiniOpenid(anyString())).thenAnswer(invocation -> {
            String openid = invocation.getArgument(0);
            if (store.viewer != null && openid.equals(store.viewer.getMiniOpenid())) {
                return Optional.of(store.viewer);
            }
            if (store.other != null && openid.equals(store.other.getMiniOpenid())) {
                return Optional.of(store.other);
            }
            return Optional.empty();
        });
        when(viewerRepository.findByPhone(anyString())).thenAnswer(invocation -> {
            String phone = invocation.getArgument(0);
            if (store.other != null && phone.equals(store.other.getPhone())) {
                return Optional.of(store.other);
            }
            if (store.viewer != null && phone.equals(store.viewer.getPhone())) {
                return Optional.of(store.viewer);
            }
            return Optional.empty();
        });
    }

    private static WechatLoginRequest wechat(String code) {
        WechatLoginRequest request = new WechatLoginRequest();
        request.setCode(code);
        request.setAgreed(true);
        return request;
    }

    private static ViewerEntity viewer(long id, int status, String phone, String miniOpenid) {
        ViewerEntity entity = new ViewerEntity();
        entity.setId(id);
        entity.setStatus(status);
        entity.setPhone(phone);
        entity.setMiniOpenid(miniOpenid);
        return entity;
    }

    private static final class Store {
        private long nextViewerId = 1;
        private ViewerEntity viewer;
        private ViewerEntity other;
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
