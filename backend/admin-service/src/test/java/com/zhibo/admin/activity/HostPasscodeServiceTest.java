package com.zhibo.admin.activity;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.activity.dto.PasscodeEnterRequest;
import com.zhibo.admin.activity.dto.PasscodeEnterResponse;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HostPasscodeServiceTest {

    private static final String ACTIVITY_ID = "a1b2c3d4e5f67890abcdef1234567890";
    private static final String PASSCODE = "274803129";

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private LiveProviderRegistry providerRegistry;
    @Mock
    private LiveProvider liveProvider;

    @InjectMocks
    private HostPasscodeService hostPasscodeService;

    @Test
    void enter_returnsHostUrlForValidPasscode() {
        ActivityEntity entity = broadcastable();
        entity.setHostPasscode(PASSCODE);
        when(activityRepository.findByHostPasscodeAndDeletedTimeIsNull(PASSCODE))
                .thenReturn(Optional.of(entity));
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.buildRoleJoinUrl(
                eq("100"), eq(1), eq("pc" + PASSCODE + "@qkzb.com"), eq("讲师"), isNull()))
                .thenReturn("https://host.example/page");

        PasscodeEnterRequest request = new PasscodeEnterRequest();
        request.setPasscode(PASSCODE);

        PasscodeEnterResponse response = hostPasscodeService.enter(request);
        assertEquals(ACTIVITY_ID, response.getActivityId());
        assertEquals("https://host.example/page", response.getHostUrl());
        assertEquals(1, response.getRole());
        assertEquals("讲师", response.getNickname());
        verify(liveProvider).buildRoleJoinUrl(
                eq("100"), eq(1), eq("pc" + PASSCODE + "@qkzb.com"), eq("讲师"), isNull());
    }

    @Test
    void enter_usesCustomNickname() {
        ActivityEntity entity = broadcastable();
        entity.setHostPasscode(PASSCODE);
        when(activityRepository.findByHostPasscodeAndDeletedTimeIsNull(PASSCODE))
                .thenReturn(Optional.of(entity));
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.buildRoleJoinUrl(
                eq("100"), eq(1), eq("pc" + PASSCODE + "@qkzb.com"), eq("余灵巧"), isNull()))
                .thenReturn("https://host.example/page");

        PasscodeEnterRequest request = new PasscodeEnterRequest();
        request.setPasscode("  " + PASSCODE + "  ");
        request.setNickname(" 余灵巧 ");

        PasscodeEnterResponse response = hostPasscodeService.enter(request);
        assertEquals("余灵巧", response.getNickname());
    }

    @Test
    void enter_rejectsUnknownPasscode() {
        when(activityRepository.findByHostPasscodeAndDeletedTimeIsNull(PASSCODE))
                .thenReturn(Optional.empty());
        PasscodeEnterRequest request = new PasscodeEnterRequest();
        request.setPasscode(PASSCODE);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> hostPasscodeService.enter(request));
        assertEquals("开播口令无效", ex.getMessage());
    }

    @Test
    void enter_rejectsWhenCannotBroadcast() {
        ActivityEntity entity = broadcastable();
        entity.setHostPasscode(PASSCODE);
        entity.setShelfStatus("pending");
        when(activityRepository.findByHostPasscodeAndDeletedTimeIsNull(PASSCODE))
                .thenReturn(Optional.of(entity));

        PasscodeEnterRequest request = new PasscodeEnterRequest();
        request.setPasscode(PASSCODE);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> hostPasscodeService.enter(request));
        assertEquals("当前状态不可开播", ex.getMessage());
    }

    @Test
    void enter_rejectsTimedRecord() {
        ActivityEntity entity = broadcastable();
        entity.setHostPasscode(PASSCODE);
        entity.setType(4);
        entity.setIsTiming(true);
        when(activityRepository.findByHostPasscodeAndDeletedTimeIsNull(PASSCODE))
                .thenReturn(Optional.of(entity));

        PasscodeEnterRequest request = new PasscodeEnterRequest();
        request.setPasscode(PASSCODE);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> hostPasscodeService.enter(request));
        assertEquals("定时直播不支持口令开播", ex.getMessage());
    }

    @Test
    void getPasscode_returnsExisting() {
        ActivityEntity entity = broadcastable();
        entity.setHostPasscode(PASSCODE);
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));

        assertEquals(PASSCODE, hostPasscodeService.getPasscode(ACTIVITY_ID).getPasscode());
    }

    @Test
    void resetPasscode_changesPasscodeWithoutEndingLive() {
        ActivityEntity entity = broadcastable();
        entity.setHostPasscode(PASSCODE);
        entity.setState(1);
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));
        when(activityRepository.existsByHostPasscode(anyString())).thenReturn(false);

        String next = hostPasscodeService.resetPasscode(ACTIVITY_ID).getPasscode();
        assertNotEquals(PASSCODE, next);
        assertEquals(9, next.length());
        assertEquals(1, entity.getState());
        verify(providerRegistry, never()).require(anyString());
        verify(liveProvider, never()).end(anyString());
        verify(activityRepository).save(entity);
    }

    private static ActivityEntity broadcastable() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(ACTIVITY_ID);
        entity.setProvider("vhall");
        entity.setProviderRoomId("100");
        entity.setTitle("测试");
        entity.setStartTime("2026-10-24 12:00:00");
        entity.setType(2);
        entity.setReady(1);
        entity.setState(2);
        entity.setRiskStatus("passed");
        entity.setShelfStatus("on");
        return entity;
    }
}
