package com.zhibo.live.viewer;

import com.zhibo.live.viewer.dto.DevPhoneLoginRequest;
import com.zhibo.live.viewer.dto.PhoneLoginRequest;
import com.zhibo.live.viewer.dto.ViewerAuthResponse;
import com.zhibo.live.viewer.dto.ViewerRefreshRequest;
import com.zhibo.live.viewer.dto.ViewerUserResponse;
import com.zhibo.live.viewer.dto.WechatLoginRequest;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRegisterChannel;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 观众登录：微信一键、手机号授权、本地明文手机号、双票刷新、当前观众。 */
@Slf4j
@Service
public class ViewerAuthService {

    private static final String AGREE_MSG = "请先阅读并同意用户协议和隐私协议";
    private static final String WECHAT_FAIL = "微信授权失败，请重试";
    private static final String PHONE_TAKEN = "该手机号已绑定其他微信";
    private static final String DISABLED = "账号已停用";
    private static final String UNAVAILABLE = "观众登录暂不可用";
    private static final String BAD_PHONE = "请输入正确的手机号";
    /** 中国大陆手机号：1 开头 11 位。 */
    private static final Pattern CN_MOBILE = Pattern.compile("^1\\d{10}$");

    private final ViewerRepository viewerRepository;
    private final WechatMpClient wechatMpClient;
    private final ViewerJwtTokenService jwtTokenService;
    private final ViewerAuthProperties properties;
    private final Clock clock;

    public ViewerAuthService(
            ViewerRepository viewerRepository,
            WechatMpClient wechatMpClient,
            ViewerJwtTokenService jwtTokenService,
            ViewerAuthProperties properties,
            Clock clock) {
        this.viewerRepository = viewerRepository;
        this.wechatMpClient = wechatMpClient;
        this.jwtTokenService = jwtTokenService;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public ViewerAuthResponse wechatLogin(WechatLoginRequest request) {
        requireAgreed(request != null && request.isAgreed());
        if (request == null || isBlank(request.getCode())) {
            throw new ViewerAuthException(400, "缺少微信登录凭证");
        }
        requireConfigured();
        WechatMpClient.WechatSession session = wechatMpClient.code2Session(request.getCode().trim());
        LocalDateTime now = LocalDateTime.now(clock);
        ViewerEntity viewer = findOrCreate(session, now);
        requireActive(viewer);
        rememberUnionid(viewer, session, now);
        markLoggedIn(viewer, now);
        return issue(viewer);
    }

    @Transactional
    public ViewerAuthResponse phoneLogin(PhoneLoginRequest request) {
        requireAgreed(request != null && request.isAgreed());
        if (request == null || isBlank(request.getLoginCode())) {
            throw new ViewerAuthException(400, "缺少微信登录凭证");
        }
        if (isBlank(request.getPhoneCode())) {
            throw new ViewerAuthException(400, "缺少手机号授权凭证");
        }
        requireConfigured();
        WechatMpClient.WechatSession session = wechatMpClient.code2Session(request.getLoginCode().trim());
        WechatMpClient.WechatPhone phone = wechatMpClient.getPhoneNumber(request.getPhoneCode().trim());
        LocalDateTime now = LocalDateTime.now(clock);
        ViewerEntity viewer = findOrCreate(session, now);
        requireActive(viewer);
        rememberUnionid(viewer, session, now);
        bindPhone(viewer, phone, now);
        return issue(viewer);
    }

    /**
     * 仅 local 环境调用：按明文手机号登录；无账号则自动注册（无 mini_openid）。
     * Controller 须先按 spring profile=local 拦掉 test/prod。
     */
    @Transactional
    public ViewerAuthResponse devPhoneLogin(DevPhoneLoginRequest request) {
        requireAgreed(request != null && request.isAgreed());
        String phone = normalizeCnMobile(request == null ? null : request.getPhone());
        requireJwtConfigured();
        LocalDateTime now = LocalDateTime.now(clock);
        ViewerEntity viewer = viewerRepository.findByPhone(phone)
                .orElseGet(() -> createViewerByPhone(phone, now));
        requireActive(viewer);
        markLoggedIn(viewer, now);
        return issue(viewer);
    }

    /** 过期 access 可以换票。不吊销旧 refresh。 */
    @Transactional
    public ViewerAuthResponse refresh(ViewerRefreshRequest request) {
        if (request == null || isBlank(request.getRefreshToken()) || isBlank(request.getAccessToken())) {
            throw new ViewerAuthException(401, "登录状态已失效，请重新登录");
        }
        long refreshViewerId = jwtTokenService.parseRefreshUserId(request.getRefreshToken().trim());
        long accessViewerId = jwtTokenService.parseAccessAllowExpired(request.getAccessToken().trim());
        if (refreshViewerId != accessViewerId) {
            throw new ViewerAuthException(401, "登录状态已失效，请重新登录");
        }
        ViewerEntity viewer = viewerRepository.findById(refreshViewerId)
                .orElseThrow(() -> new ViewerAuthException(403, DISABLED));
        requireActive(viewer);
        return issue(viewer);
    }

    @Transactional(readOnly = true)
    public ViewerUserResponse userInfo(long viewerId) {
        ViewerEntity viewer = viewerRepository.findById(viewerId)
                .orElseThrow(() -> new ViewerAuthException(401, "未登录"));
        if (!ViewerStatus.isNormal(viewer.getStatus())) {
            throw new ViewerAuthException(403, DISABLED);
        }
        return toUser(viewer);
    }

    private ViewerEntity findOrCreate(WechatMpClient.WechatSession session, LocalDateTime now) {
        if (session == null || isBlank(session.openid())) {
            throw new ViewerAuthException(400, WECHAT_FAIL);
        }
        String miniOpenid = session.openid().trim();
        return viewerRepository.findByMiniOpenid(miniOpenid)
                .orElseGet(() -> createViewer(miniOpenid, session.unionid(), now));
    }

    private ViewerEntity createViewer(String miniOpenid, String unionid, LocalDateTime now) {
        ViewerEntity viewer = new ViewerEntity();
        viewer.setMiniOpenid(miniOpenid);
        if (!isBlank(unionid)) {
            viewer.setUnionid(unionid.trim());
        }
        viewer.setRegisterChannel(ViewerRegisterChannel.WECHAT);
        viewer.setStatus(ViewerStatus.NORMAL);
        viewer.setAgreedTime(now);
        viewer.setLastLoginTime(now);
        viewer.setLastVisitTime(now);
        viewer.setCreatedTime(now);
        viewer.setUpdatedTime(now);
        try {
            return viewerRepository.save(viewer);
        } catch (DataIntegrityViolationException ex) {
            log.warn("观众 mini_openid 已存在，改为读取已有行");
            return viewerRepository.findByMiniOpenid(miniOpenid)
                    .orElseThrow(() -> new ViewerAuthException(400, WECHAT_FAIL));
        }
    }

    /** local 造号：只写手机号，不写 mini_openid；渠道仍记微信（本地联调造数）。 */
    private ViewerEntity createViewerByPhone(String phone, LocalDateTime now) {
        ViewerEntity viewer = new ViewerEntity();
        viewer.setPhone(phone);
        viewer.setPhoneCountryCode("86");
        viewer.setRegisterChannel(ViewerRegisterChannel.WECHAT);
        viewer.setStatus(ViewerStatus.NORMAL);
        viewer.setAgreedTime(now);
        viewer.setLastLoginTime(now);
        viewer.setLastVisitTime(now);
        viewer.setCreatedTime(now);
        viewer.setUpdatedTime(now);
        try {
            return viewerRepository.save(viewer);
        } catch (DataIntegrityViolationException ex) {
            log.warn("观众手机号已存在，改为读取已有行");
            return viewerRepository.findByPhone(phone)
                    .orElseThrow(() -> new ViewerAuthException(400, BAD_PHONE));
        }
    }

    private static String normalizeCnMobile(String raw) {
        if (raw == null) {
            throw new ViewerAuthException(400, BAD_PHONE);
        }
        String phone = raw.trim();
        if (!CN_MOBILE.matcher(phone).matches()) {
            throw new ViewerAuthException(400, BAD_PHONE);
        }
        return phone;
    }

    /** 微信这次带回了 unionid 才写。没带回就保留原来的。 */
    private void rememberUnionid(ViewerEntity viewer, WechatMpClient.WechatSession session, LocalDateTime now) {
        if (viewer == null || session == null || isBlank(session.unionid())) {
            return;
        }
        String unionid = session.unionid().trim();
        if (unionid.equals(viewer.getUnionid())) {
            return;
        }
        viewer.setUnionid(unionid);
        viewer.setUpdatedTime(now);
        viewerRepository.save(viewer);
    }

    private void bindPhone(ViewerEntity viewer, WechatMpClient.WechatPhone phone, LocalDateTime now) {
        String number = phone == null || phone.purePhoneNumber() == null ? "" : phone.purePhoneNumber().trim();
        if (number.isEmpty() || number.length() > 20) {
            throw new ViewerAuthException(400, WECHAT_FAIL);
        }
        viewerRepository.findByPhone(number).ifPresent(owner -> {
            if (owner.getId() == null || !owner.getId().equals(viewer.getId())) {
                throw new ViewerAuthException(400, PHONE_TAKEN);
            }
        });
        viewer.setPhone(number);
        if (phone.countryCode() != null && !phone.countryCode().isBlank()) {
            String country = phone.countryCode().trim();
            if (country.length() <= 8) {
                viewer.setPhoneCountryCode(country);
            }
        }
        try {
            markLoggedIn(viewer, now);
        } catch (DataIntegrityViolationException ex) {
            log.warn("观众手机号唯一约束冲突 viewerId={}", viewer.getId());
            throw new ViewerAuthException(400, PHONE_TAKEN);
        }
    }

    private void markLoggedIn(ViewerEntity viewer, LocalDateTime now) {
        viewer.setAgreedTime(now);
        viewer.setLastLoginTime(now);
        viewer.setLastVisitTime(now);
        viewer.setUpdatedTime(now);
        viewerRepository.save(viewer);
    }

    private ViewerAuthResponse issue(ViewerEntity viewer) {
        ViewerAuthResponse response = new ViewerAuthResponse();
        response.setAccessToken(jwtTokenService.issueAccess(viewer.getId()));
        response.setExpiresIn(jwtTokenService.expireSeconds());
        response.setRefreshToken(jwtTokenService.issueRefresh(viewer.getId()));
        response.setRefreshExpiresIn(jwtTokenService.refreshExpireSeconds());
        response.setUser(toUser(viewer));
        return response;
    }

    private static ViewerUserResponse toUser(ViewerEntity viewer) {
        ViewerUserResponse user = new ViewerUserResponse();
        user.setId(viewer.getId());
        boolean hasPhone = !isBlank(viewer.getPhone());
        user.setHasPhone(hasPhone);
        if (hasPhone) {
            user.setPhoneMasked(ViewerPhoneMask.mask(viewer.getPhone()));
        }
        if (!isBlank(viewer.getNickname())) {
            user.setNickname(viewer.getNickname().trim());
        }
        if (!isBlank(viewer.getAvatarUrl())) {
            user.setAvatarUrl(viewer.getAvatarUrl().trim());
        }
        if (!isBlank(viewer.getAvatarObjectKey())) {
            user.setAvatarObjectKey(viewer.getAvatarObjectKey().trim());
        }
        return user;
    }

    private void requireActive(ViewerEntity viewer) {
        if (viewer == null || !ViewerStatus.isNormal(viewer.getStatus())) {
            throw new ViewerAuthException(403, DISABLED);
        }
    }

    private void requireAgreed(boolean agreed) {
        if (!agreed) {
            throw new ViewerAuthException(400, AGREE_MSG);
        }
    }

    /** 缺哪一项只写日志，响应里不点名。 */
    private void requireConfigured() {
        boolean missing = false;
        if (properties.jwtSecretMissing()) {
            log.warn("观众登录配置缺失: VIEWER_JWT_SECRET");
            missing = true;
        }
        ViewerAuthProperties.Wechat wechat = properties.getWechat();
        if (wechat == null || isBlank(wechat.getMpAppId())) {
            log.warn("观众登录配置缺失: WECHAT_MP_APP_ID");
            missing = true;
        }
        if (wechat == null || isBlank(wechat.getMpAppSecret())) {
            log.warn("观众登录配置缺失: WECHAT_MP_APP_SECRET");
            missing = true;
        }
        if (missing) {
            throw new ViewerAuthException(500, UNAVAILABLE);
        }
    }

    /** local 明文手机号登录只需 JWT；不依赖微信密钥。 */
    private void requireJwtConfigured() {
        if (properties.jwtSecretMissing()) {
            log.warn("观众登录配置缺失: VIEWER_JWT_SECRET");
            throw new ViewerAuthException(500, UNAVAILABLE);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
