package com.zhibo.admin.activity;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityMemberEntity;
import com.zhibo.activity.ActivityMemberId;
import com.zhibo.activity.ActivityMemberRepository;
import com.zhibo.activity.ActivityMemberRole;
import com.zhibo.admin.activity.dto.ActivityHostResponse;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.liveprovider.LiveProviderRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 开播 / 角色参会页：调云侧 get-role-url，本站页用返回的 page_url 做 iframe。
 * 见 https://vhall.apifox.cn/234257715e0
 */
@Service
@RequiredArgsConstructor
public class HostService {

    private static final String ROLE_EMAIL_DOMAIN = "@qkzb.com";
    private static final int VHALL_NICKNAME_MAX = 30;

    private final ActivityService activityService;
    private final LiveProviderRegistry providerRegistry;
    private final ActivityMemberRepository memberRepository;
    private final SysUserAccountRepository userRepository;

    /**
     * @param roleCode 本站开播角色（与微吼 type 一致）；空则按主播
     * @param userId   当前登录员工；嘉宾 / 助理须为本场该角色成员
     */
    public ActivityHostResponse host(String activityId, Integer roleCode, long userId) {
        ActivityEntity entity = activityService.requireActivity(activityId);
        if (!BroadcastRules.canBroadcast(
                entity.getReady(), entity.getRiskStatus(), entity.getShelfStatus(), entity.getState())) {
            throw new IllegalArgumentException("当前状态不可开播");
        }
        ActivityMemberRole role = roleCode == null
                ? ActivityMemberRole.HOST
                : ActivityMemberRole.requireCode(roleCode);

        Optional<ActivityMemberEntity> memberOpt = memberRepository
                .findById(new ActivityMemberId(entity.getId(), userId));

        if (role != ActivityMemberRole.HOST) {
            ActivityMemberEntity member = memberOpt
                    .orElseThrow(() -> new IllegalArgumentException("你不是本场的" + role.getLabel()));
            if (member.getRole() != role.getCode()) {
                ActivityMemberRole actual = ActivityMemberRole.requireCode(member.getRole());
                throw new IllegalArgumentException("你已是本场的" + actual.getLabel() + "，不能以" + role.getLabel() + "身份进入");
            }
        }

        SysUserAccountEntity user = userRepository.findById(userId)
                .filter(u -> u.getRsStatus() != null && u.getRsStatus() == 1)
                .orElseThrow(() -> new IllegalArgumentException(
                        role == ActivityMemberRole.HOST ? "用户无效" : "成员无效"));

        // 所有角色都传 email；本场昵称/头像优先（成员表有行则用），否则员工资料
        String email = userId + ROLE_EMAIL_DOMAIN;
        ActivityMemberEntity member = memberOpt.orElse(null);
        String nickname = resolveNickname(member, user);
        String avatar = resolveAvatar(member, user);

        String hostUrl = providerRegistry.require(entity.getProvider())
                .buildRoleJoinUrl(entity.getProviderRoomId(), role.getCode(), email, nickname, avatar);

        ActivityHostResponse response = new ActivityHostResponse();
        response.setProvider(entity.getProvider());
        response.setHostUrl(hostUrl);
        response.setRole(role.getCode());
        return response;
    }

    private static String resolveNickname(ActivityMemberEntity member, SysUserAccountEntity user) {
        if (member != null) {
            String fromMember = trimToNull(member.getNickname());
            if (fromMember != null) {
                return truncate(fromMember, VHALL_NICKNAME_MAX);
            }
        }
        String fromUser = trimToNull(user.getUserName());
        if (fromUser != null) {
            return truncate(fromUser, VHALL_NICKNAME_MAX);
        }
        return "用户" + user.getId();
    }

    private static String resolveAvatar(ActivityMemberEntity member, SysUserAccountEntity user) {
        if (member != null) {
            String fromMember = trimToNull(member.getAvatarUrl());
            if (fromMember != null) {
                return fromMember;
            }
        }
        String head = trimToNull(user.getHeadimgurl());
        if (head != null) {
            return head;
        }
        return trimToNull(user.getUserAvatar());
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
