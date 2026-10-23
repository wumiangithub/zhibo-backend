package com.zhibo.admin.activity;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityMemberEntity;
import com.zhibo.activity.ActivityMemberId;
import com.zhibo.activity.ActivityMemberRepository;
import com.zhibo.activity.ActivityMemberRole;
import com.zhibo.admin.activity.dto.ActivityHostResponse;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HostServiceTest {

    private static final long USER_ID = 10001L;
    private static final String ROLE_EMAIL = USER_ID + "@qkzb.com";

    @Mock
    private ActivityService activityService;
    @Mock
    private LiveProviderRegistry providerRegistry;
    @Mock
    private LiveProvider liveProvider;
    @Mock
    private ActivityMemberRepository memberRepository;
    @Mock
    private SysUserAccountRepository userRepository;

    @InjectMocks
    private HostService hostService;

    @Test
    void host_returnsPageUrlForHost() {
        ActivityEntity entity = broadcastable("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        when(activityService.requireActivity(entity.getId())).thenReturn(entity);
        when(memberRepository.findById(new ActivityMemberId(entity.getId(), USER_ID)))
                .thenReturn(Optional.empty());

        SysUserAccountEntity user = activeUser("员工名", null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.buildRoleJoinUrl(
                eq("100"), eq(1), eq(ROLE_EMAIL), eq("员工名"), isNull()))
                .thenReturn("https://host.example/page");

        ActivityHostResponse response = hostService.host(entity.getId(), null, USER_ID);
        assertEquals("vhall", response.getProvider());
        assertEquals("https://host.example/page", response.getHostUrl());
        assertEquals(1, response.getRole());
    }

    @Test
    void host_usesMemberNicknameAndAvatarWhenPresent() {
        ActivityEntity entity = broadcastable("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        when(activityService.requireActivity(entity.getId())).thenReturn(entity);

        ActivityMemberEntity member = new ActivityMemberEntity();
        member.setId(new ActivityMemberId(entity.getId(), USER_ID));
        member.setRole(ActivityMemberRole.HOST.getCode());
        member.setNickname("wumian1");
        member.setAvatarUrl("https://cdn.example/host.png?x=1&y=2");
        when(memberRepository.findById(new ActivityMemberId(entity.getId(), USER_ID)))
                .thenReturn(Optional.of(member));

        SysUserAccountEntity user = activeUser("员工名", "https://cdn.example/staff.png");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.buildRoleJoinUrl(
                eq("100"),
                eq(ActivityMemberRole.HOST.getCode()),
                eq(ROLE_EMAIL),
                eq("wumian1"),
                eq("https://cdn.example/host.png?x=1&y=2")))
                .thenReturn("https://host.example/custom");

        ActivityHostResponse response = hostService.host(
                entity.getId(), ActivityMemberRole.HOST.getCode(), USER_ID);
        assertEquals("https://host.example/custom", response.getHostUrl());
        assertEquals(1, response.getRole());
    }

    @Test
    void host_guestUsesMemberNicknameAndEmail() {
        ActivityEntity entity = broadcastable("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        when(activityService.requireActivity(entity.getId())).thenReturn(entity);

        ActivityMemberEntity member = new ActivityMemberEntity();
        member.setId(new ActivityMemberId(entity.getId(), USER_ID));
        member.setRole(ActivityMemberRole.GUEST.getCode());
        member.setNickname("场上嘉宾");
        member.setAvatarUrl("https://cdn.example/a.png");
        when(memberRepository.findById(new ActivityMemberId(entity.getId(), USER_ID)))
                .thenReturn(Optional.of(member));

        SysUserAccountEntity user = activeUser("员工名", null);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.buildRoleJoinUrl(
                eq("100"),
                eq(ActivityMemberRole.GUEST.getCode()),
                eq(ROLE_EMAIL),
                eq("场上嘉宾"),
                eq("https://cdn.example/a.png")))
                .thenReturn("https://guest.example/page");

        ActivityHostResponse response = hostService.host(
                entity.getId(), ActivityMemberRole.GUEST.getCode(), USER_ID);
        assertEquals("https://guest.example/page", response.getHostUrl());
        assertEquals(2, response.getRole());
    }

    @Test
    void host_rejectsWhenCannotBroadcast() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        entity.setProvider("vhall");
        entity.setProviderRoomId("100");
        entity.setRiskStatus("pending");
        entity.setShelfStatus("on");
        entity.setState(2);
        when(activityService.requireActivity(entity.getId())).thenReturn(entity);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> hostService.host(entity.getId(), null, USER_ID));
        assertEquals("当前状态不可开播", ex.getMessage());
        verify(activityService).requireActivity(entity.getId());
        verify(providerRegistry, never()).require(eq("vhall"));
        verifyNoInteractions(liveProvider);
    }

    private static SysUserAccountEntity activeUser(String userName, String headimgurl) {
        SysUserAccountEntity user = new SysUserAccountEntity();
        user.setId(USER_ID);
        user.setRsStatus(1);
        user.setUserName(userName);
        user.setHeadimgurl(headimgurl);
        return user;
    }

    private static ActivityEntity broadcastable(String id) {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(id);
        entity.setProvider("vhall");
        entity.setProviderRoomId("100");
        entity.setRiskStatus("passed");
        entity.setShelfStatus("on");
        entity.setState(2);
        return entity;
    }
}
