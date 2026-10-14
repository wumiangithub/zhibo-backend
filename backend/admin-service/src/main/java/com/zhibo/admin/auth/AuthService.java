package com.zhibo.admin.auth;

import com.zhibo.admin.auth.dto.AuthUserResponse;
import com.zhibo.admin.auth.dto.CaptchaResponse;
import com.zhibo.admin.auth.dto.LoginPendingResponse;
import com.zhibo.admin.auth.dto.LoginQueryResponse;
import com.zhibo.admin.auth.dto.LoginRequest;
import com.zhibo.admin.auth.dto.RefreshRequest;
import com.zhibo.admin.auth.dto.RefreshResponse;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AuthService {

    private static final String MSG_BAD_CREDENTIALS = "账号或密码错误";
    private static final String MSG_UNBOUND = "该账号未绑定微信，请联系管理员绑定";
    private static final String MSG_MISMATCH = "微信与账号不匹配，请使用已绑定的微信扫码";
    private static final String MSG_BOUND_OTHER = "该微信已绑定其他账号";

    private final CaptchaService captchaService;
    private final PasswordCryptoService passwordCryptoService;
    private final ReplayGuard replayGuard;
    private final SysUserAccountRepository userRepository;
    private final LoginStateStore loginStateStore;
    private final JwtTokenService jwtTokenService;
    private final WechatOAuthClient wechatOAuthClient;
    private final AuthProperties authProperties;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(
            CaptchaService captchaService,
            PasswordCryptoService passwordCryptoService,
            ReplayGuard replayGuard,
            SysUserAccountRepository userRepository,
            LoginStateStore loginStateStore,
            JwtTokenService jwtTokenService,
            WechatOAuthClient wechatOAuthClient,
            AuthProperties authProperties) {
        this.captchaService = captchaService;
        this.passwordCryptoService = passwordCryptoService;
        this.replayGuard = replayGuard;
        this.userRepository = userRepository;
        this.loginStateStore = loginStateStore;
        this.jwtTokenService = jwtTokenService;
        this.wechatOAuthClient = wechatOAuthClient;
        this.authProperties = authProperties;
    }

    public CaptchaResponse captcha() {
        return captchaService.create();
    }

    public LoginPendingResponse login(LoginRequest request) {
        captchaService.verifyAndConsume(request.getCaptchaId(), request.getCaptchaCode());

        PasswordCryptoService.DecryptedPassword decrypted =
                passwordCryptoService.decrypt(request.getPassword());
        replayGuard.consumeOrReject(decrypted.requestId());

        SysUserAccountEntity user = userRepository.findByUserAccount(request.getUserAccount().trim())
                .orElseThrow(() -> new IllegalArgumentException(MSG_BAD_CREDENTIALS));

        if (user.getRsStatus() == null || user.getRsStatus() != 1) {
            throw new AuthException(403, "账号已停用");
        }

        if (user.getPassword() == null
                || !passwordEncoder.matches(decrypted.password(), user.getPassword())) {
            throw new IllegalArgumentException(MSG_BAD_CREDENTIALS);
        }

        String state = loginStateStore.createPending(user.getId(), LoginStateStore.DEFAULT_EXPIRES_IN);
        return new LoginPendingResponse(state, LoginStateStore.DEFAULT_EXPIRES_IN);
    }

    public void handleWechatCallback(String code, String state) {
        LoginStateStore.State session = loginStateStore.get(state);
        if (session == null) {
            throw new IllegalArgumentException("登录会话无效或已过期");
        }
        if (session.getStatus() != LoginStateStore.Status.PENDING) {
            return;
        }
        if (code == null || code.isBlank()) {
            loginStateStore.markFailed(state, "微信授权失败，请重试");
            return;
        }

        WechatOAuthClient.WechatIdentity identity;
        try {
            identity = wechatOAuthClient.exchangeCode(code);
        } catch (IllegalArgumentException ex) {
            loginStateStore.markFailed(state, ex.getMessage());
            return;
        } catch (Exception ex) {
            log.warn("微信回调换票失败: {}", ex.getMessage());
            loginStateStore.markFailed(state, "微信授权失败，请重试");
            return;
        }

        Optional<SysUserAccountEntity> userOpt = userRepository.findById(session.getUserId());
        if (userOpt.isEmpty()) {
            loginStateStore.markFailed(state, MSG_BAD_CREDENTIALS);
            return;
        }
        SysUserAccountEntity user = userOpt.get();
        boolean unbound = isBlank(user.getOpenid()) && isBlank(user.getUnionid());

        if (unbound) {
            if (!authProperties.getWechat().isAllowSelfBind()) {
                loginStateStore.markFailed(state, MSG_UNBOUND);
                return;
            }
            if (wechatBoundToOther(identity, user.getId())) {
                loginStateStore.markFailed(state, MSG_BOUND_OTHER);
                return;
            }
            user.setOpenid(identity.openid());
            if (!isBlank(identity.unionid())) {
                user.setUnionid(identity.unionid());
            }
            userRepository.save(user);
            log.info("本地自助绑定微信: userId={} openid={}", user.getId(), identity.openid());
            markSuccess(state, user);
            return;
        }

        if (wechatBoundToOther(identity, user.getId())) {
            loginStateStore.markFailed(state, MSG_BOUND_OTHER);
            return;
        }

        boolean matched;
        if (!isBlank(user.getUnionid())) {
            matched = user.getUnionid().equals(identity.unionid());
        } else {
            matched = user.getOpenid().equals(identity.openid());
        }
        if (!matched) {
            loginStateStore.markFailed(state, MSG_MISMATCH);
            return;
        }

        markSuccess(state, user);
    }

    private boolean wechatBoundToOther(WechatOAuthClient.WechatIdentity identity, Long userId) {
        if (!isBlank(identity.unionid())) {
            Optional<SysUserAccountEntity> byUnion = userRepository.findByUnionid(identity.unionid());
            if (byUnion.isPresent() && !byUnion.get().getId().equals(userId)) {
                return true;
            }
        }
        if (!isBlank(identity.openid())) {
            Optional<SysUserAccountEntity> byOpen = userRepository.findByOpenid(identity.openid());
            if (byOpen.isPresent() && !byOpen.get().getId().equals(userId)) {
                return true;
            }
        }
        return false;
    }

    private void markSuccess(String state, SysUserAccountEntity user) {
        AuthUserResponse profile = toUserResponse(user);
        String accessToken = jwtTokenService.issue(user.getId(), user.getUserAccount());
        String refreshToken = jwtTokenService.issueRefresh(user.getId(), user.getUserAccount());
        loginStateStore.markSuccess(
                state,
                accessToken,
                jwtTokenService.expireSeconds(),
                refreshToken,
                jwtTokenService.refreshExpireSeconds(),
                profile);
    }

    public LoginQueryResponse query(String state) {
        LoginQueryResponse resp = new LoginQueryResponse();
        if (state == null || state.isBlank()) {
            throw new IllegalArgumentException("缺少 state");
        }
        LoginStateStore.State session = loginStateStore.get(state);
        if (session == null) {
            resp.setStatus(3);
            return resp;
        }
        return switch (session.getStatus()) {
            case PENDING -> {
                resp.setStatus(0);
                yield resp;
            }
            case SUCCESS -> {
                LoginStateStore.State consumed = loginStateStore.consumeSuccess(state);
                resp.setStatus(1);
                if (consumed != null) {
                    resp.setAccessToken(consumed.getAccessToken());
                    resp.setExpiresIn(consumed.getExpiresIn());
                    resp.setRefreshToken(consumed.getRefreshToken());
                    resp.setRefreshExpiresIn(consumed.getRefreshExpiresIn());
                    resp.setUser(consumed.getUser());
                }
                yield resp;
            }
            case FAILED -> {
                resp.setStatus(2);
                resp.setErrorMsg(session.getErrorMsg());
                yield resp;
            }
            case EXPIRED -> {
                resp.setStatus(3);
                yield resp;
            }
        };
    }

    public RefreshResponse refresh(RefreshRequest request) {
        long refreshUserId = jwtTokenService.parseRefreshUserId(request.getRefreshToken());
        long accessUserId = jwtTokenService.parseAccessAllowExpired(request.getAccessToken());
        if (accessUserId != refreshUserId) {
            throw new AuthException(401, "刷新凭证与登录凭证不匹配");
        }
        SysUserAccountEntity user = userRepository.findById(refreshUserId)
                .orElseThrow(() -> new AuthException(403, "账号不存在或已停用"));
        if (user.getRsStatus() == null || user.getRsStatus() != 1) {
            throw new AuthException(403, "账号不存在或已停用");
        }
        return new RefreshResponse(
                jwtTokenService.issue(user.getId(), user.getUserAccount()),
                jwtTokenService.expireSeconds(),
                jwtTokenService.issueRefresh(user.getId(), user.getUserAccount()),
                jwtTokenService.refreshExpireSeconds());
    }

    public AuthUserResponse me(long userId) {
        SysUserAccountEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(401, "未登录"));
        if (user.getRsStatus() == null || user.getRsStatus() != 1) {
            throw new AuthException(403, "账号已停用");
        }
        return toUserResponse(user);
    }

    public static AuthUserResponse toUserResponse(SysUserAccountEntity user) {
        String avatar = !isBlank(user.getHeadimgurl())
                ? user.getHeadimgurl()
                : user.getUserAvatar();
        return new AuthUserResponse(user.getId(), user.getUserAccount(), user.getUserName(), avatar);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
