package com.zhibo.admin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.admin.auth.dto.LoginPendingResponse;
import com.zhibo.admin.auth.dto.LoginQueryResponse;
import com.zhibo.admin.auth.dto.LoginRequest;
import com.zhibo.admin.auth.dto.RefreshRequest;
import com.zhibo.admin.auth.dto.RefreshResponse;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private CaptchaService captchaService;
    @Mock
    private SysUserAccountRepository userRepository;
    @Mock
    private WechatOAuthClient wechatOAuthClient;

    private PasswordCryptoService passwordCrypto;
    private AuthProperties properties;
    private AuthService authService;
    private JwtTokenService jwtTokenService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-10-13T04:00:00Z"), ZoneOffset.UTC);
        properties = new AuthProperties();
        properties.setPasswordAesKeyBase64(aesKey());
        properties.setJwtSecret("ae0536f9-1234-5678-8e13-5a19ed505da0");
        properties.setJwtExpireSeconds(7200);
        properties.setJwtIssuer("base-admin-api");
        properties.setJwtAudience("base");

        passwordCrypto = new PasswordCryptoService(properties, new ObjectMapper());
        jwtTokenService = new JwtTokenService(properties, clock);
        authService = new AuthService(
                captchaService,
                passwordCrypto,
                new ReplayGuard(clock),
                userRepository,
                new LoginStateStore(clock),
                jwtTokenService,
                wechatOAuthClient,
                properties);

        lenient().doNothing().when(captchaService).verifyAndConsume(anyString(), anyString());
    }

    @Test
    void login_success_returnsStateWithoutJwt() {
        when(userRepository.findByUserAccount("admin"))
                .thenReturn(Optional.of(activeUser(1L, "admin", "pass123", "oid-1", "uid-1")));

        LoginPendingResponse pending = authService.login(buildLogin("admin", "pass123"));
        assertNotNull(pending.getState());
        assertTrue(pending.getState().startsWith("NCRM_"));
        assertEquals(180, pending.getExpiresIn());
    }

    @Test
    void login_disabled_throws403() {
        SysUserAccountEntity user = activeUser(1L, "admin", "pass123", "oid-1", "uid-1");
        user.setRsStatus(0);
        when(userRepository.findByUserAccount("admin")).thenReturn(Optional.of(user));

        AuthException ex = assertThrows(AuthException.class,
                () -> authService.login(buildLogin("admin", "pass123")));
        assertEquals(403, ex.getCode());
    }

    @Test
    void login_wrongPassword_throws400() {
        when(userRepository.findByUserAccount("admin"))
                .thenReturn(Optional.of(activeUser(1L, "admin", "pass123", "oid-1", "uid-1")));

        assertThrows(IllegalArgumentException.class,
                () -> authService.login(buildLogin("admin", "wrong")));
    }

    @Test
    void wechatCallback_unbound_marksFailed() {
        properties.getWechat().setAllowSelfBind(false);
        SysUserAccountEntity user = activeUser(1L, "admin", "pass123", null, null);
        when(userRepository.findByUserAccount("admin")).thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        LoginPendingResponse pending = authService.login(buildLogin("admin", "pass123"));
        when(wechatOAuthClient.exchangeCode("code-1"))
                .thenReturn(new WechatOAuthClient.WechatIdentity("oid-x", "uid-x"));

        authService.handleWechatCallback("code-1", pending.getState());

        LoginQueryResponse query = authService.query(pending.getState());
        assertEquals(2, query.getStatus());
        assertEquals("该账号未绑定微信，请联系管理员绑定", query.getErrorMsg());
        verify(userRepository, never()).save(any());
        assertNull(user.getOpenid());
    }

    @Test
    void wechatCallback_unbound_selfBindEnabled_writesAndSuccess() {
        properties.getWechat().setAllowSelfBind(true);
        SysUserAccountEntity user = activeUser(2L, "admin2", "pass123", null, null);
        when(userRepository.findByUserAccount("admin2")).thenReturn(Optional.of(user));
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(userRepository.findByUnionid("uid-new")).thenReturn(Optional.empty());
        when(userRepository.findByOpenid("oid-new")).thenReturn(Optional.empty());
        when(userRepository.save(user)).thenReturn(user);

        LoginPendingResponse pending = authService.login(buildLogin("admin2", "pass123"));
        when(wechatOAuthClient.exchangeCode("code-1"))
                .thenReturn(new WechatOAuthClient.WechatIdentity("oid-new", "uid-new"));

        authService.handleWechatCallback("code-1", pending.getState());

        assertEquals("oid-new", user.getOpenid());
        assertEquals("uid-new", user.getUnionid());
        verify(userRepository).save(user);

        LoginQueryResponse query = authService.query(pending.getState());
        assertEquals(1, query.getStatus());
        assertNotNull(query.getAccessToken());
        assertEquals(2L, query.getUser().getId());
    }

    @Test
    void wechatCallback_boundMismatch_stillFails_evenIfSelfBindOn() {
        properties.getWechat().setAllowSelfBind(true);
        SysUserAccountEntity user = activeUser(1L, "admin", "pass123", "oid-1", "uid-1");
        when(userRepository.findByUserAccount("admin")).thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByUnionid("uid-other")).thenReturn(Optional.empty());
        when(userRepository.findByOpenid("oid-other")).thenReturn(Optional.empty());

        LoginPendingResponse pending = authService.login(buildLogin("admin", "pass123"));
        when(wechatOAuthClient.exchangeCode("code-1"))
                .thenReturn(new WechatOAuthClient.WechatIdentity("oid-other", "uid-other"));

        authService.handleWechatCallback("code-1", pending.getState());

        LoginQueryResponse query = authService.query(pending.getState());
        assertEquals(2, query.getStatus());
        assertEquals("微信与账号不匹配，请使用已绑定的微信扫码", query.getErrorMsg());
        verify(userRepository, never()).save(any());
        assertEquals("oid-1", user.getOpenid());
        assertEquals("uid-1", user.getUnionid());
    }

    @Test
    void wechatCallback_match_thenQueryConsumesJwt() {
        SysUserAccountEntity user = activeUser(1L, "admin", "pass123", "oid-1", "uid-1");
        when(userRepository.findByUserAccount("admin")).thenReturn(Optional.of(user));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userRepository.findByUnionid("uid-1")).thenReturn(Optional.of(user));
        when(userRepository.findByOpenid(anyString())).thenReturn(Optional.of(user));

        LoginPendingResponse pending = authService.login(buildLogin("admin", "pass123"));
        when(wechatOAuthClient.exchangeCode("code-1"))
                .thenReturn(new WechatOAuthClient.WechatIdentity("oid-1", "uid-1"));

        authService.handleWechatCallback("code-1", pending.getState());

        LoginQueryResponse query = authService.query(pending.getState());
        assertEquals(1, query.getStatus());
        assertNotNull(query.getAccessToken());
        assertNotNull(query.getRefreshToken());
        assertEquals(7200, query.getExpiresIn());
        assertEquals(604800, query.getRefreshExpiresIn());
        assertEquals(1L, query.getUser().getId());

        LoginQueryResponse again = authService.query(pending.getState());
        assertEquals(3, again.getStatus());
    }

    @Test
    void refresh_expiredAccess_reissuesFromDatabase() {
        SysUserAccountEntity user = activeUser(1L, "admin-renamed", "pass123", "oid-1", "uid-1");
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        RefreshRequest request = new RefreshRequest();
        request.setAccessToken(expiredAccess(1L, "admin"));
        request.setRefreshToken(jwtTokenService.issueRefresh(1L, "admin", "测试用户", 0, "admin", "1"));

        RefreshResponse response = authService.refresh(request);
        assertEquals(7200, response.getExpiresIn());
        assertEquals(604800, response.getRefreshExpiresIn());
        assertEquals(1L, jwtTokenService.parseUserId(response.getAccessToken()));
        Claims claims = Jwts.parser()
                .verifyWith(signingKey())
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(response.getAccessToken())
                .getPayload();
        assertEquals("admin-renamed", claims.get("user_account", String.class));
        assertEquals(JwtTokenService.TOKEN_TYPE_ACCESS, claims.get("token_type", String.class));
        assertEquals("admin-renamed", claims.get("identifier", String.class));
        assertEquals("1", claims.get("yhid", String.class));
        assertEquals("测试用户", claims.get("yg_name", String.class));
    }

    @Test
    void refresh_mismatch_throws401() {
        RefreshRequest request = new RefreshRequest();
        request.setAccessToken(jwtTokenService.issue(1L, "admin", "测试用户", 0, "admin", "1"));
        request.setRefreshToken(jwtTokenService.issueRefresh(2L, "other", "其他", 0, "other", "2"));

        AuthException ex = assertThrows(AuthException.class, () -> authService.refresh(request));
        assertEquals(401, ex.getCode());
    }

    @Test
    void refresh_expiredRefresh_throws401() {
        RefreshRequest request = new RefreshRequest();
        request.setAccessToken(jwtTokenService.issue(1L, "admin", "测试用户", 0, "admin", "1"));
        request.setRefreshToken(expiredRefresh(1L));

        AuthException ex = assertThrows(AuthException.class, () -> authService.refresh(request));
        assertEquals(401, ex.getCode());
    }

    @Test
    void refresh_disabled_throws403() {
        SysUserAccountEntity user = activeUser(1L, "admin", "pass123", "oid-1", "uid-1");
        user.setRsStatus(0);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        RefreshRequest request = new RefreshRequest();
        request.setAccessToken(jwtTokenService.issue(1L, "admin", "测试用户", 0, "admin", "1"));
        request.setRefreshToken(jwtTokenService.issueRefresh(1L, "admin", "测试用户", 0, "admin", "1"));

        AuthException ex = assertThrows(AuthException.class, () -> authService.refresh(request));
        assertEquals(403, ex.getCode());
    }

    private String expiredAccess(long userId, String account) {
        return signedToken(userId, account, "base", JwtTokenService.TOKEN_TYPE_ACCESS, clock.instant().minusSeconds(60));
    }

    private String expiredRefresh(long userId) {
        return signedToken(userId, "admin", "base-refresh", JwtTokenService.TOKEN_TYPE_REFRESH, clock.instant().minusSeconds(60));
    }

    private String signedToken(
            long userId, String account, String audience, String tokenType, Instant expiration) {
        return Jwts.builder()
                .issuer("base-admin-api")
                .audience().add(audience).and()
                .subject(String.valueOf(userId))
                .claim("user_id", userId)
                .claim("user_account", account)
                .claim("user_name", "测试用户")
                .claim("user_flag", 0)
                .claim("token_type", tokenType)
                .issuedAt(Date.from(expiration.minusSeconds(7200)))
                .expiration(Date.from(expiration))
                .signWith(signingKey())
                .compact();
    }

    private SecretKey signingKey() {
        byte[] bytes = properties.getJwtSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(bytes);
    }

    private LoginRequest buildLogin(String account, String password) {
        LoginRequest req = new LoginRequest();
        req.setUserAccount(account);
        req.setPassword(passwordCrypto.encrypt(UUID.randomUUID().toString(), password));
        req.setCaptchaId("cid");
        req.setCaptchaCode("ab12");
        return req;
    }

    private static SysUserAccountEntity activeUser(
            long id, String account, String rawPassword, String openid, String unionid) {
        SysUserAccountEntity user = new SysUserAccountEntity();
        user.setId(id);
        user.setUserAccount(account);
        user.setUserName("测试用户");
        user.setRsStatus(1);
        user.setYhid(id);
        user.setPassword(new BCryptPasswordEncoder().encode(rawPassword));
        user.setOpenid(openid);
        user.setUnionid(unionid);
        return user;
    }

    private static String aesKey() {
        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) {
            key[i] = (byte) (i + 1);
        }
        return Base64.getEncoder().encodeToString(key);
    }
}
