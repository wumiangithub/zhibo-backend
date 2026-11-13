package com.zhibo.admin.console;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityMemberEntity;
import com.zhibo.activity.ActivityMemberId;
import com.zhibo.activity.ActivityMemberRepository;
import com.zhibo.activity.ActivityReady;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.RoomCommentEntity;
import com.zhibo.activity.RoomCommentRepository;
import com.zhibo.activity.RoomCommentType;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.admin.console.dto.ConsoleQuestionHistoryResponse;
import com.zhibo.admin.console.dto.ConsoleSdkSignResponse;
import com.zhibo.admin.console.dto.ConsoleSummaryResponse;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.ProviderRoomSnapshot;
import com.zhibo.liveprovider.dto.WatchSdkBundle;
import com.zhibo.liveprovider.dto.WatchSdkCommand;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsoleServiceTest {

    private static final String ACTIVITY_ID = UUID.randomUUID().toString().replace("-", "");
    private static final long EMPLOYEE_ID = 10001L;

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivityMemberRepository memberRepository;
    @Mock
    private SysUserAccountRepository userRepository;
    @Mock
    private RoomCommentRepository commentRepository;
    @Mock
    private LiveProviderRegistry providerRegistry;
    @Mock
    private LiveProvider liveProvider;
    @Mock
    private com.zhibo.activity.cloud.ActivityCloudSyncService activityCloudSyncService;

    private ConsoleService service;

    @BeforeEach
    void setUp() {
        service = new ConsoleService(
                activityRepository,
                memberRepository,
                userRepository,
                commentRepository,
                providerRegistry,
                activityCloudSyncService,
                Clock.fixed(Instant.parse("2026-11-06T00:00:00Z"), ZoneOffset.UTC));
        org.mockito.Mockito.lenient()
                .doNothing()
                .when(activityCloudSyncService)
                .syncQuietlyAsync(any(), any());
    }

    @Test
    void summary_mapsActivityShellFields() {
        ActivityEntity entity = baseActivity();
        entity.setTitle("中控台场次");
        entity.setState(1);
        entity.setType(0);
        entity.setStartTime("2026-11-06 10:00:00");
        entity.setCoverUrl("https://cdn.example/cover.jpg");
        entity.setPlaybackEnabled(true);
        entity.setPlaybackValidityType("forever");
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));

        ConsoleSummaryResponse res = service.summary(ACTIVITY_ID);

        assertEquals(ACTIVITY_ID, res.getId());
        assertEquals("vhall", res.getProvider());
        assertEquals("中控台场次", res.getTitle());
        assertEquals("https://cdn.example/cover.jpg", res.getCoverUrl());
        assertEquals(1, res.getState());
        assertEquals("forever", res.getPlaybackValidityType());
        assertFalse(Boolean.TRUE.equals(res.getPlaybackExpired()));
        verify(activityRepository, never()).save(any());
    }

    @Test
    void summary_delegatesCloudSyncToFacadeAsync() {
        ActivityEntity entity = baseActivity();
        entity.setProviderRoomId("room-1");
        entity.setTitle("再开播场");
        entity.setState(3);
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));

        ConsoleSummaryResponse res = service.summary(ACTIVITY_ID);

        // 异步旁路：本响应仍为本库 state，不 await 云写回
        assertEquals(3, res.getState());
        verify(activityCloudSyncService).syncQuietlyAsync(
                eq(entity), eq(com.zhibo.activity.cloud.ActivityCloudSyncService.Options.consoleSummary()));
        verify(activityCloudSyncService, never()).syncQuietly(any(), any());
    }

    @Test
    void sdkSign_usesStaffEmailAndMemberNickname() {
        ActivityEntity entity = baseActivity();
        entity.setProviderRoomId("room-1");
        entity.setReady(ActivityReady.OK);
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));

        SysUserAccountEntity employee = new SysUserAccountEntity();
        employee.setId(EMPLOYEE_ID);
        employee.setUserName("张三");
        employee.setHeadimgurl("http://cdn/e.png");
        when(userRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));

        ActivityMemberEntity member = new ActivityMemberEntity();
        member.setId(new ActivityMemberId(ACTIVITY_ID, EMPLOYEE_ID));
        member.setNickname("本场助理");
        member.setAvatarUrl("http://cdn/m.png");
        when(memberRepository.findById(new ActivityMemberId(ACTIVITY_ID, EMPLOYEE_ID)))
                .thenReturn(Optional.of(member));

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        WatchSdkBundle.SdkInit sdk = new WatchSdkBundle.SdkInit();
        sdk.setAccount("z10001");
        sdk.setEmail("z10001@qkzb.com");
        sdk.setUsername("本场助理");
        String expectedHead = "http://cdn/m.png?v=z" + EMPLOYEE_ID;
        sdk.setHead(expectedHead);
        sdk.setWebinarId("room-1");
        sdk.setAppKey("key");
        sdk.setSignedAt("1");
        sdk.setSignType(0);
        sdk.setSign("abc");
        WatchSdkBundle bundle = new WatchSdkBundle();
        bundle.setSdk(sdk);
        when(liveProvider.buildWatchSdk(any(WatchSdkCommand.class))).thenReturn(bundle);

        ConsoleSdkSignResponse res = service.sdkSign(ACTIVITY_ID, null, EMPLOYEE_ID);

        assertEquals(EMPLOYEE_ID, res.getEmployeeId());
        assertEquals("本场助理", res.getNickname());
        assertEquals("z10001@qkzb.com", res.getSdk().getEmail());
        assertEquals("z10001", res.getSdk().getAccount());

        ArgumentCaptor<WatchSdkCommand> captor = ArgumentCaptor.forClass(WatchSdkCommand.class);
        verify(liveProvider).buildWatchSdk(captor.capture());
        WatchSdkCommand cmd = captor.getValue();
        assertEquals("z10001@qkzb.com", cmd.email());
        assertEquals(EMPLOYEE_ID, cmd.viewerId());
        assertEquals("本场助理", cmd.nickname());
        assertEquals(expectedHead, cmd.head());
    }

    @Test
    void sdkSign_rejectsMissingRoom() {
        ActivityEntity entity = baseActivity();
        entity.setProviderRoomId(null);
        entity.setReady(ActivityReady.OK);
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> service.sdkSign(ACTIVITY_ID, null, EMPLOYEE_ID));
        assertEquals("直播间尚未就绪", ex.getMessage());
    }

    @Test
    void questionHistory_mapsQuestionAndAnswer() {
        ActivityEntity entity = baseActivity();
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));

        RoomCommentEntity row = new RoomCommentEntity();
        row.setQuestionId("2397558");
        row.setUsername("12");
        row.setNickname("甲");
        row.setContent("怎么报名");
        row.setMsgId("msg_q1");
        row.setRoleName(2);
        row.setCommentType(RoomCommentType.QUESTION);
        row.setCommentTime(LocalDateTime.of(2026, 11, 6, 10, 0, 0));
        row.setAnswerContent("点链接");
        row.setAnswerNickname("主持");
        row.setAnswerRoleName(1);
        when(commentRepository.findByActivityIdAndCommentTypeAndDeletedTimeIsNullOrderByCommentTimeAsc(
                        eq(ACTIVITY_ID), eq(RoomCommentType.QUESTION)))
                .thenReturn(List.of(row));

        ConsoleQuestionHistoryResponse res = service.questionHistory(ACTIVITY_ID);
        assertEquals(1, res.getTotal());
        assertEquals(1, res.getList().size());
        assertEquals("2397558", res.getList().get(0).getQuestionId());
        assertEquals("12", res.getList().get(0).getSenderId());
        assertEquals("甲", res.getList().get(0).getSenderName());
        assertEquals("怎么报名", res.getList().get(0).getMsg());
        assertEquals("点链接", res.getList().get(0).getAnswerContent());
    }

    @Test
    void questionHistory_skipsBlankContent() {
        ActivityEntity entity = baseActivity();
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));

        RoomCommentEntity blank = new RoomCommentEntity();
        blank.setQuestionId("1");
        blank.setNickname("甲");
        blank.setContent("  ");
        blank.setCommentTime(LocalDateTime.now());
        when(commentRepository.findByActivityIdAndCommentTypeAndDeletedTimeIsNullOrderByCommentTimeAsc(
                        eq(ACTIVITY_ID), eq(RoomCommentType.QUESTION)))
                .thenReturn(List.of(blank));

        ConsoleQuestionHistoryResponse res = service.questionHistory(ACTIVITY_ID);
        assertEquals(1, res.getTotal());
        assertTrue(res.getList().isEmpty());
    }

    @Test
    void resolveNickname_prefersQueryThenMemberThenEmployee() {
        SysUserAccountEntity employee = new SysUserAccountEntity();
        employee.setUserName("员工名");
        ActivityMemberEntity member = new ActivityMemberEntity();
        member.setNickname("成员昵称");

        assertEquals("查询昵称", ConsoleService.resolveConsoleNickname("查询昵称", member, employee));
        assertEquals("成员昵称", ConsoleService.resolveConsoleNickname(null, member, employee));
        assertEquals("员工名", ConsoleService.resolveConsoleNickname(null, null, employee));
        assertEquals("助理", ConsoleService.resolveConsoleNickname(null, null, null));
    }

    @Test
    void sdkSign_blankAvatarUsesDefaultHeadWithEmployeeQuery() {
        ActivityEntity entity = baseActivity();
        entity.setProviderRoomId("room-1");
        entity.setReady(ActivityReady.OK);
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(entity));

        SysUserAccountEntity employee = new SysUserAccountEntity();
        employee.setId(EMPLOYEE_ID);
        employee.setUserName("张三");
        when(userRepository.findById(EMPLOYEE_ID)).thenReturn(Optional.of(employee));
        when(memberRepository.findById(new ActivityMemberId(ACTIVITY_ID, EMPLOYEE_ID)))
                .thenReturn(Optional.empty());

        String expectedHead = com.zhibo.common.SdkAvatarHeads.PLACEHOLDER_BASE_URL + "?v=z" + EMPLOYEE_ID;
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        WatchSdkBundle.SdkInit sdk = new WatchSdkBundle.SdkInit();
        sdk.setAccount("z" + EMPLOYEE_ID);
        sdk.setEmail("z" + EMPLOYEE_ID + "@qkzb.com");
        sdk.setUsername("张三");
        sdk.setHead(expectedHead);
        WatchSdkBundle bundle = new WatchSdkBundle();
        bundle.setSdk(sdk);
        when(liveProvider.buildWatchSdk(any(WatchSdkCommand.class))).thenReturn(bundle);

        ConsoleSdkSignResponse res = service.sdkSign(ACTIVITY_ID, null, EMPLOYEE_ID);
        assertEquals(expectedHead, res.getSdk().getHead());

        ArgumentCaptor<WatchSdkCommand> captor = ArgumentCaptor.forClass(WatchSdkCommand.class);
        verify(liveProvider).buildWatchSdk(captor.capture());
        assertEquals(expectedHead, captor.getValue().head());
    }

    private static ActivityEntity baseActivity() {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(ACTIVITY_ID);
        entity.setProvider("vhall");
        entity.setTitle("t");
        entity.setState(2);
        entity.setType(0);
        return entity;
    }
}
