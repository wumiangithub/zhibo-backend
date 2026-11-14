package com.zhibo.admin.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhibo.admin.auth.dto.AuthUserResponse;
import com.zhibo.admin.auth.dto.CaptchaResponse;
import com.zhibo.admin.auth.dto.LoginPendingResponse;
import com.zhibo.admin.auth.dto.LoginQueryResponse;
import com.zhibo.admin.auth.dto.LoginRequest;
import com.zhibo.admin.auth.dto.RefreshRequest;
import com.zhibo.admin.auth.dto.RefreshResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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
    private final Environment environment;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(
            CaptchaService captchaService,
            PasswordCryptoService passwordCryptoService,
            ReplayGuard replayGuard,
            SysUserAccountRepository userRepository,
            LoginStateStore loginStateStore,
            JwtTokenService jwtTokenService,
            WechatOAuthClient wechatOAuthClient,
            AuthProperties authProperties,
            Environment environment) {
        this.captchaService = captchaService;
        this.passwordCryptoService = passwordCryptoService;
        this.replayGuard = replayGuard;
        this.userRepository = userRepository;
        this.loginStateStore = loginStateStore;
        this.jwtTokenService = jwtTokenService;
        this.wechatOAuthClient = wechatOAuthClient;
        this.authProperties = authProperties;
        this.environment = environment;
    }

    public CaptchaResponse captcha() {
        return captchaService.create();
    }


    /**
     * 仅 local / test：按账号直接发 token，不校验密码、不走扫码。
     * 账号必须已存在且未停用。
     */
    public LoginQueryResponse devAccountLogin(String userAccount) {
        String account = userAccount == null ? "" : userAccount.trim();
        if (account.isEmpty()) {
            throw new IllegalArgumentException("请输入账号");
        }
        SysUserAccountEntity user = userRepository.findByUserAccount(account)
                .orElseThrow(() -> new IllegalArgumentException("账号不存在"));
        if (user.getRsStatus() == null || user.getRsStatus() != 1) {
            throw new AuthException(403, "账号已停用");
        }
        LoginQueryResponse resp = new LoginQueryResponse();
        resp.setStatus(1);
        resp.setAccessToken(issueAccess(user));
        resp.setExpiresIn((int) jwtTokenService.expireSeconds());
        resp.setRefreshToken(issueRefresh(user));
        resp.setRefreshExpiresIn((int) jwtTokenService.refreshExpireSeconds());
        resp.setUser(toUserResponse(user));
        log.info("账号直登（仅 local/test）: userId={} account={}", user.getId(), user.getUserAccount());
        return resp;
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

    /**
     * 仅 local / test 且配置开启时生效；uat / prod 即使误配也不模拟扫码成功。
     */
    private boolean mockScanSuccessActive() {
        if (!authProperties.getWechat().isMockScanSuccess()) {
            return false;
        }
        if (!environment.acceptsProfiles(Profiles.of("local", "test"))) {
            log.warn("当前环境忽略 WECHAT_MOCK_SCAN_SUCCESS（仅 local/test 生效）");
            return false;
        }
        return true;
    }

    /**
     * 测试权宜：不等微信回调，把已通过账号密码校验的 pending state 直接标成功。
     * 真实回调仍走 {@link #handleWechatCallback}；关闭 WECHAT_MOCK_SCAN_SUCCESS 后本方法不会被调用。
     */
    private LoginStateStore.State mockScanSuccess(String state, LoginStateStore.State session) {
        Optional<SysUserAccountEntity> userOpt = userRepository.findById(session.getUserId());
        if (userOpt.isEmpty()
                || userOpt.get().getRsStatus() == null
                || userOpt.get().getRsStatus() != 1) {
            loginStateStore.markFailed(state, "账号不存在或已停用");
            log.warn("微信扫码轮询按配置直接返回失败（账号不可用，未走真实回调）: stateUserId={}",
                    session.getUserId());
            return loginStateStore.get(state);
        }
        SysUserAccountEntity user = userOpt.get();
        markSuccess(state, user);
        log.info("微信扫码轮询按配置直接返回成功（未走真实回调）: userId={}", user.getId());
        return loginStateStore.get(state);
    }

    private void markSuccess(String state, SysUserAccountEntity user) {
        AuthUserResponse profile = toUserResponse(user);
        String accessToken = issueAccess(user);
        String refreshToken = issueRefresh(user);
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
        if (session.getStatus() == LoginStateStore.Status.PENDING && mockScanSuccessActive()) {
            session = mockScanSuccess(state, session);
            if (session == null) {
                resp.setStatus(3);
                return resp;
            }
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
                issueAccess(user),
                jwtTokenService.expireSeconds(),
                issueRefresh(user),
                jwtTokenService.refreshExpireSeconds());
    }

    private String issueAccess(SysUserAccountEntity user) {
        return jwtTokenService.issue(
                user.getId(),
                user.getUserAccount(),
                user.getUserName(),
                userFlag(user),
                salerIdentifier(user),
                salerYhid(user));
    }

    private String issueRefresh(SysUserAccountEntity user) {
        return jwtTokenService.issueRefresh(
                user.getId(),
                user.getUserAccount(),
                user.getUserName(),
                userFlag(user),
                salerIdentifier(user),
                salerYhid(user));
    }

    private static int userFlag(SysUserAccountEntity user) {
        return user.getUserFlg() == null ? 0 : user.getUserFlg();
    }

    /** oms-go：有 CRM {@code code} 用 code，否则用登录账号。 */
    private static String salerIdentifier(SysUserAccountEntity user) {
        if (user.getCode() != null && !user.getCode().isBlank()) {
            return user.getCode().trim();
        }
        return user.getUserAccount() == null ? "" : user.getUserAccount().trim();
    }

    private static String salerYhid(SysUserAccountEntity user) {
        long yhid = user.getYhid() == null ? 0L : user.getYhid();
        return String.valueOf(yhid);
    }

    public AuthUserResponse me(long userId) {
        SysUserAccountEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AuthException(401, "未登录"));
        if (user.getRsStatus() == null || user.getRsStatus() != 1) {
            throw new AuthException(403, "账号已停用");
        }
        return toUserResponse(user);
    }

    private static final ObjectMapper JSON = new ObjectMapper();

    public static AuthUserResponse toUserResponse(SysUserAccountEntity user) {
        return new AuthUserResponse(
                user.getId(),
                user.getUserAccount(),
                user.getUserName(),
                resolveAvatar(user),
                resolveDepartmentName(user));
    }

    /** 优先微信头像 headimgurl，其次 user_avatar；都空则 null（Jackson 不输出）。 */
    public static String resolveAvatar(SysUserAccountEntity user) {
        if (!isBlank(user.getHeadimgurl())) {
            return user.getHeadimgurl().trim();
        }
        if (!isBlank(user.getUserAvatar())) {
            return user.getUserAvatar().trim();
        }
        return null;
    }

    /**
     * 从 {@code qw_bmxx} 拼部门展示名；多部门用顿号连接；空/脏数据则 null。
     */
    public static String resolveDepartmentName(SysUserAccountEntity user) {
        String raw = user.getQwBmxx();
        if (isBlank(raw) || "null".equalsIgnoreCase(raw.trim()) || "[]".equals(raw.trim())) {
            return null;
        }
        try {
            JsonNode arr = JSON.readTree(raw.trim());
            if (!arr.isArray() || arr.isEmpty()) {
                return null;
            }
            List<String> names = new ArrayList<>();
            for (JsonNode node : arr) {
                JsonNode nameNode = node.get("department_name");
                if (nameNode != null && !isBlank(nameNode.asText())) {
                    names.add(nameNode.asText().trim());
                }
            }
            return names.isEmpty() ? null : String.join("、", names);
        } catch (Exception ex) {
            return null;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
