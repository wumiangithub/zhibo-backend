package com.zhibo.live.viewer;

import com.zhibo.live.viewer.dto.PhoneLoginRequest;
import com.zhibo.live.viewer.dto.ViewerAuthResponse;
import com.zhibo.live.viewer.dto.ViewerRefreshRequest;
import com.zhibo.live.viewer.dto.ViewerUserResponse;
import com.zhibo.live.viewer.dto.WechatLoginRequest;
import com.zhibo.viewer.ViewerChannel;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerIdentityEntity;
import com.zhibo.viewer.ViewerIdentityRepository;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 观众登录：微信一键、手机号授权、双票刷新、当前观众。 */
@Slf4j
@Service
public class ViewerAuthService {

    private static final String AGREE_MSG = "请先阅读并同意用户协议和隐私协议";
    private static final String WECHAT_FAIL = "微信授权失败，请重试";
    private static final String PHONE_TAKEN = "该手机号已绑定其他微信";
    private static final String DISABLED = "账号已停用";
    private static final String UNAVAILABLE = "观众登录暂不可用";

    private final ViewerRepository viewerRepository;
    private final ViewerIdentityRepository identityRepository;
    private final WechatMpClient wechatMpClient;
    private final ViewerJwtTokenService jwtTokenService;
    private final ViewerAuthProperties properties;
    private final Clock clock;

    public ViewerAuthService(
            ViewerRepository viewerRepository,
            ViewerIdentityRepository identityRepository,
            WechatMpClient wechatMpClient,
            ViewerJwtTokenService jwtTokenService,
            ViewerAuthProperties properties,
            Clock clock) {
        this.viewerRepository = viewerRepository;
        this.identityRepository = identityRepository;
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
        rememberUnionid(session, now);
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
        rememberUnionid(session, now);
        bindPhone(viewer, phone, now);
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
    public ViewerUserResponse me(long viewerId) {
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
        String openid = session.openid().trim();
        return identityRepository.findByChannelAndOpenid(ViewerChannel.WECHAT_MP, openid)
                .map(identity -> viewerRepository.findById(identity.getViewerId())
                        .orElseThrow(() -> new ViewerAuthException(403, DISABLED)))
                .orElseGet(() -> createViewer(openid, session.unionid(), now));
    }

    private ViewerEntity createViewer(String openid, String unionid, LocalDateTime now) {
        ViewerEntity viewer = new ViewerEntity();
        viewer.setStatus(ViewerStatus.NORMAL);
        viewer.setAgreedTime(now);
        viewer.setLastLoginTime(now);
        viewer.setCreatedTime(now);
        viewer.setUpdatedTime(now);
        viewer = viewerRepository.save(viewer);

        ViewerIdentityEntity identity = new ViewerIdentityEntity();
        identity.setViewerId(viewer.getId());
        identity.setChannel(ViewerChannel.WECHAT_MP);
        identity.setOpenid(openid);
        if (!isBlank(unionid)) {
            identity.setUnionid(unionid.trim());
        }
        identity.setCreatedTime(now);
        identity.setUpdatedTime(now);
        try {
            identityRepository.save(identity);
        } catch (DataIntegrityViolationException ex) {
            log.warn("观众身份已存在，改为读取已有小程序身份");
            return identityRepository.findByChannelAndOpenid(ViewerChannel.WECHAT_MP, openid)
                    .flatMap(existing -> viewerRepository.findById(existing.getViewerId()))
                    .orElseThrow(() -> new ViewerAuthException(400, WECHAT_FAIL));
        }
        return viewer;
    }

    /** 微信这次带回了 unionid 才写。没带回就保留原来的。 */
    private void rememberUnionid(WechatMpClient.WechatSession session, LocalDateTime now) {
        if (session == null || isBlank(session.unionid()) || isBlank(session.openid())) {
            return;
        }
        String unionid = session.unionid().trim();
        identityRepository.findByChannelAndOpenid(ViewerChannel.WECHAT_MP, session.openid().trim())
                .ifPresent(identity -> {
                    if (unionid.equals(identity.getUnionid())) {
                        return;
                    }
                    identity.setUnionid(unionid);
                    identity.setUpdatedTime(now);
                    identityRepository.save(identity);
                });
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

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
