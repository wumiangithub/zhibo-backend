package com.zhibo.live.watch;

import com.zhibo.activity.ActivityAudienceEntity;
import com.zhibo.activity.ActivityAudienceRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.SellPasswordCryptoService;
import com.zhibo.live.watch.dto.VerifyPasswordResponse;
import com.zhibo.live.watch.dto.WatchAccessResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchAccessServiceTest {

    private static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    private static final Instant NOW = LocalDateTime.of(2026, 10, 30, 12, 0)
            .atZone(SHANGHAI).toInstant();

    @Mock
    private WatchService watchService;
    @Mock
    private ActivityAudienceRepository audienceRepository;
    @Mock
    private SellPasswordCryptoService sellPasswordCrypto;
    @Mock
    private ActivityAccessTokenService activityAccessTokenService;

    private WatchAccessService service;

    @BeforeEach
    void setUp() {
        service = new WatchAccessService(
                watchService,
                audienceRepository,
                sellPasswordCrypto,
                activityAccessTokenService,
                Clock.fixed(NOW, SHANGHAI));
    }

    @Test
    void free_ok() {
        ActivityEntity a = base("free", true);
        when(watchService.requireActivity("aid")).thenReturn(a);
        WatchAccessResponse r = service.access("aid", null, null);
        assertEquals(WatchAccessService.STATE_OK, r.getAccessState());
    }

    @Test
    void sellAloneFalse_okEvenEncrypted() {
        ActivityEntity a = base("encrypted", false);
        when(watchService.requireActivity("aid")).thenReturn(a);
        WatchAccessResponse r = service.access("aid", null, null);
        assertEquals(WatchAccessService.STATE_OK, r.getAccessState());
    }

    @Test
    void encrypted_needPassword() {
        ActivityEntity a = base("encrypted", true);
        when(watchService.requireActivity("aid")).thenReturn(a);
        when(activityAccessTokenService.isValidForActivity(null, "aid")).thenReturn(false);
        WatchAccessResponse r = service.access("aid", null, null);
        assertEquals(WatchAccessService.STATE_NEED_PASSWORD, r.getAccessState());
    }

    @Test
    void encrypted_withToken_ok() {
        ActivityEntity a = base("encrypted", true);
        when(watchService.requireActivity("aid")).thenReturn(a);
        when(activityAccessTokenService.isValidForActivity("tok", "aid")).thenReturn(true);
        WatchAccessResponse r = service.access("aid", null, "tok");
        assertEquals(WatchAccessService.STATE_OK, r.getAccessState());
    }

    @Test
    void encrypted_daysNotStarted_stillNeedPasswordNotExpired() {
        ActivityEntity a = base("encrypted", true);
        a.setAccessValidityType("days");
        a.setAccessValidityDays(7);
        a.setLiveActualEndTime(null);
        when(watchService.requireActivity("aid")).thenReturn(a);
        when(activityAccessTokenService.isValidForActivity(null, "aid")).thenReturn(false);
        WatchAccessResponse r = service.access("aid", null, null);
        assertEquals(WatchAccessService.STATE_NEED_PASSWORD, r.getAccessState());
        assertEquals(false, r.getAccessExpired());
    }

    @Test
    void encrypted_daysExpired() {
        ActivityEntity a = base("encrypted", true);
        a.setAccessValidityType("days");
        a.setAccessValidityDays(7);
        a.setLiveActualEndTime(LocalDateTime.of(2026, 10, 1, 12, 0));
        when(watchService.requireActivity("aid")).thenReturn(a);
        WatchAccessResponse r = service.access("aid", null, "tok");
        assertEquals(WatchAccessService.STATE_EXPIRED, r.getAccessState());
        verify(activityAccessTokenService, never()).isValidForActivity(any(), any());
    }

    @Test
    void specified_needLogin() {
        ActivityEntity a = base("specified", true);
        when(watchService.requireActivity("aid")).thenReturn(a);
        WatchAccessResponse r = service.access("aid", null, null);
        assertEquals(WatchAccessService.STATE_NEED_LOGIN, r.getAccessState());
    }

    @Test
    void specified_forbidden() {
        ActivityEntity a = base("specified", true);
        when(watchService.requireActivity("aid")).thenReturn(a);
        when(audienceRepository.findByActivityIdAndViewerId("aid", 9L)).thenReturn(Optional.empty());
        WatchAccessResponse r = service.access("aid", 9L, null);
        assertEquals(WatchAccessService.STATE_FORBIDDEN, r.getAccessState());
    }

    @Test
    void specified_okWithEntitlement() {
        ActivityEntity a = base("specified", true);
        when(watchService.requireActivity("aid")).thenReturn(a);
        ActivityAudienceEntity row = new ActivityAudienceEntity();
        row.setEntitlementStatus(1);
        when(audienceRepository.findByActivityIdAndViewerId("aid", 9L)).thenReturn(Optional.of(row));
        WatchAccessResponse r = service.access("aid", 9L, null);
        assertEquals(WatchAccessService.STATE_OK, r.getAccessState());
    }

    @Test
    void verifyPassword_ok() {
        ActivityEntity a = base("encrypted", true);
        a.setSellPasswordCipher("cipher");
        when(watchService.requireActivity("aid")).thenReturn(a);
        when(sellPasswordCrypto.decrypt("cipher")).thenReturn("123456");
        when(activityAccessTokenService.defaultExpireSeconds()).thenReturn(86400);
        when(activityAccessTokenService.issue(eq("aid"), anyInt())).thenReturn("jwt");
        VerifyPasswordResponse r = service.verifyPassword("aid", "123456");
        assertEquals("jwt", r.getAccessToken());
        assertEquals(86400, r.getExpiresIn());
    }

    @Test
    void verifyPassword_wrong() {
        ActivityEntity a = base("encrypted", true);
        a.setSellPasswordCipher("cipher");
        when(watchService.requireActivity("aid")).thenReturn(a);
        when(sellPasswordCrypto.decrypt("cipher")).thenReturn("123456");
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> service.verifyPassword("aid", "000000"));
        assertEquals("密码错误", ex.getMessage());
    }

    @Test
    void assertAllowed_throws403() {
        ActivityEntity a = base("encrypted", true);
        when(watchService.requireActivity("aid")).thenReturn(a);
        when(activityAccessTokenService.isValidForActivity(null, "aid")).thenReturn(false);
        WatchAccessException ex = assertThrows(
                WatchAccessException.class,
                () -> service.assertAllowed("aid", null, null));
        assertEquals(403, ex.getCode());
        assertEquals("请输入观看密码", ex.getMessage());
    }

    private static ActivityEntity base(String sellType, boolean sellAlone) {
        ActivityEntity a = new ActivityEntity();
        a.setId("aid");
        a.setSellType(sellType);
        a.setSellAlone(sellAlone);
        a.setAccessValidityType("long");
        return a;
    }
}
