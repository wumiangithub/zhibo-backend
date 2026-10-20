package com.zhibo.admin.activity;

import com.zhibo.activity.ActivityAuditEntity;
import com.zhibo.activity.ActivityAuditRepository;
import com.zhibo.activity.ActivityCoursewareRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityMemberEntity;
import com.zhibo.activity.ActivityMemberId;
import com.zhibo.activity.ActivityMemberRepository;
import com.zhibo.activity.ActivityMemberRole;
import com.zhibo.activity.ActivityOwnerRepository;
import com.zhibo.activity.ActivityProductRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.activity.dto.AcceptActivityMemberRequest;
import com.zhibo.admin.activity.dto.AcceptActivityMemberResponse;
import com.zhibo.admin.activity.dto.ActivityListResponse;
import com.zhibo.admin.activity.dto.ActivityPushStreamResponse;
import com.zhibo.admin.activity.dto.ActivityRecordListResponse;
import com.zhibo.admin.activity.dto.ActivityStreamStatusResponse;
import com.zhibo.admin.activity.dto.AuditListResponse;
import com.zhibo.admin.activity.dto.AuditRequest;
import com.zhibo.admin.activity.dto.AuditResultResponse;
import com.zhibo.admin.activity.dto.CreateActivityRequest;
import com.zhibo.admin.activity.dto.CreateActivityResponse;
import com.zhibo.admin.activity.dto.DeleteActivityResponse;
import com.zhibo.admin.activity.dto.PlaybackUpdateRequest;
import com.zhibo.admin.activity.dto.SalesUpdateRequest;
import com.zhibo.admin.activity.dto.SalesUpdateResponse;
import com.zhibo.admin.activity.dto.ShelfRequest;
import com.zhibo.admin.activity.dto.StartActivityResponse;
import com.zhibo.admin.activity.dto.UpdateActivityMembersRequest;
import com.zhibo.admin.activity.dto.UpdateActivityRequest;
import com.zhibo.admin.auth.SysUserAccountEntity;
import com.zhibo.admin.auth.SysUserAccountRepository;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.ProviderNotAvailableException;
import com.zhibo.liveprovider.dto.CreateRoomCommand;
import com.zhibo.liveprovider.dto.CreateRoomResult;
import com.zhibo.liveprovider.dto.ProviderRecordItem;
import com.zhibo.liveprovider.dto.ProviderRecordList;
import com.zhibo.liveprovider.dto.ProviderRoomSnapshot;
import com.zhibo.liveprovider.dto.PushStreamInfo;
import com.zhibo.livereport.LiveReportEntity;
import com.zhibo.livereport.LiveReportRepository;
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialRepository;
import com.zhibo.vhall.exception.VhallException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

    private static final long OPERATOR_ID = 10001L;

    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private LiveProviderRegistry providerRegistry;
    @Mock
    private LiveProvider liveProvider;
    @Mock
    private ActivityOwnerRepository ownerRepository;
    @Mock
    private ActivityProductRepository productRepository;
    @Mock
    private ActivityAuditRepository auditRepository;
    @Mock
    private ActivityMemberRepository memberRepository;
    @Mock
    private ActivityCoursewareRepository coursewareRepository;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private SysUserAccountRepository userRepository;
    @Mock
    private SellPasswordCryptoService sellPasswordCrypto;
    @Mock
    private LiveReportRepository liveReportRepository;

    @InjectMocks
    private ActivityService activityService;

    @Test
    void create_persistsAndReturnsUuid() {
        CreateActivityRequest request = baseCreateRequest();
        request.setTitle("  测试活动  ");
        request.setEndTime("2026-10-01 22:00:00");
        request.setIntroduction("  一场简介  ");
        request.setUltraLowLatency(true);
        request.setAutoList(true);

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(operator()));
        when(userRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(operator()));
        when(liveProvider.create(any(CreateRoomCommand.class)))
                .thenReturn(new CreateRoomResult("123456"));
        when(liveProvider.fetchRoom("123456")).thenReturn(new ProviderRoomSnapshot(
                "测试活动", 2, 2, 0, null, "https://share", "2026-10-01 20:00:00",
                null, "一场简介", 0, null));
        when(activityRepository.saveAndFlush(any(ActivityEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateActivityResponse response = activityService.create(request, OPERATOR_ID);

        ArgumentCaptor<CreateRoomCommand> cmd = ArgumentCaptor.forClass(CreateRoomCommand.class);
        verify(liveProvider).create(cmd.capture());
        assertEquals("测试活动", cmd.getValue().title());
        assertEquals(2, cmd.getValue().type());
        assertEquals("一场简介", cmd.getValue().introduction());
        assertEquals(true, cmd.getValue().ultraLowLatency());

        ArgumentCaptor<ActivityEntity> entity = ArgumentCaptor.forClass(ActivityEntity.class);
        verify(activityRepository).saveAndFlush(entity.capture());
        assertEquals(response.getId(), entity.getValue().getId());
        assertEquals("vhall", entity.getValue().getProvider());
        assertEquals("123456", entity.getValue().getProviderRoomId());
        assertEquals("2026-10-01 22:00:00", entity.getValue().getEndTime());
        assertEquals(true, entity.getValue().getUltraLowLatency());
        assertEquals("pending", entity.getValue().getShelfStatus());
        assertEquals("pending", entity.getValue().getRiskStatus());
        assertEquals(true, entity.getValue().getAutoList());
        assertEquals("free", entity.getValue().getSellType());
        assertEquals(true, entity.getValue().getSellAlone());
        assertEquals("长期有效", entity.getValue().getValidityLabel());
        assertEquals(true, entity.getValue().getPlaybackEnabled());
        verify(memberRepository).deleteByActivityIdAndRole(response.getId(), ActivityMemberRole.HOST.getCode());
        verify(memberRepository).saveAll(any());
        verify(auditRepository).save(argThat((ActivityAuditEntity a) ->
                "submit".equals(a.getAction())
                        && a.getResult() == null
                        && OPERATOR_ID == a.getOperatorId()
                        && "余灵巧".equals(a.getOperatorName())
                        && response.getId().equals(a.getActivityId())));
    }

    @Test
    void create_rejectsListed() {
        CreateActivityRequest request = baseCreateRequest();
        request.setListed(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.create(request, OPERATOR_ID));
        assertEquals("请改用 autoList，不再支持 listed", ex.getMessage());
    }

    @Test
    void create_requiresHost() {
        CreateActivityRequest request = baseCreateRequest();
        request.setHostIds(List.of());

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.create(request, OPERATOR_ID));
        assertEquals("主播至少选择1人", ex.getMessage());
    }

    @Test
    void create_type4_requiresRecordId() {
        CreateActivityRequest request = baseCreateRequest();
        request.setTitle("录播");
        request.setIntroduction("简介");
        request.setType(4);

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(operator()));
        when(userRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(operator()));
        when(liveProvider.create(any(CreateRoomCommand.class)))
                .thenThrow(new IllegalArgumentException("录播须填写 recordId"));

        assertThrows(IllegalArgumentException.class, () -> activityService.create(request, OPERATOR_ID));
    }

    @Test
    void create_rejectsUnknownProvider() {
        CreateActivityRequest request = baseCreateRequest();
        request.setProvider("aliyun");

        when(providerRegistry.require("aliyun")).thenThrow(new ProviderNotAvailableException());

        assertThrows(ProviderNotAvailableException.class, () -> activityService.create(request, OPERATOR_ID));
    }

    @Test
    void create_rejectsEndTimeNotAfterStart() {
        CreateActivityRequest request = baseCreateRequest();
        request.setEndTime("2026-10-01 20:00:00");

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.create(request, OPERATOR_ID));
        assertEquals("结束时间须晚于开始时间", ex.getMessage());
    }

    @Test
    void create_rejectsInvalidCourseware() {
        CreateActivityRequest request = baseCreateRequest();
        request.setCoursewareMaterialIds(List.of("m1"));

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(operator()));
        when(materialRepository.findAllById(anyCollection())).thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.create(request, OPERATOR_ID));
        assertEquals("课件无效", ex.getMessage());
    }

    @Test
    void create_bindsPassedReport() {
        String reportId = "11111111-1111-1111-1111-111111111111";
        CreateActivityRequest request = baseCreateRequest();
        request.setReportId(reportId);
        LiveReportEntity report = passedReport(reportId, OPERATOR_ID);

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(operator()));
        when(userRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(operator()));
        when(liveReportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(liveProvider.create(any(CreateRoomCommand.class)))
                .thenReturn(new CreateRoomResult("123456"));
        when(liveProvider.fetchRoom("123456")).thenReturn(new ProviderRoomSnapshot(
                "活动", 2, 2, 0, null, null, "2026-10-01 20:00:00",
                null, null, 0, null));
        when(activityRepository.saveAndFlush(any(ActivityEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(liveReportRepository.save(any(LiveReportEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateActivityResponse response = activityService.create(request, OPERATOR_ID);

        ArgumentCaptor<ActivityEntity> entity = ArgumentCaptor.forClass(ActivityEntity.class);
        verify(activityRepository).saveAndFlush(entity.capture());
        assertEquals(response.getId(), entity.getValue().getId());
        assertEquals(reportId, entity.getValue().getReportId());
        assertEquals(1, report.getBoundLiveCount());
        verify(liveReportRepository).save(report);
    }

    @Test
    void create_rejectsNonPassedReport() {
        String reportId = "11111111-1111-1111-1111-111111111111";
        CreateActivityRequest request = baseCreateRequest();
        request.setReportId(reportId);
        LiveReportEntity report = passedReport(reportId, OPERATOR_ID);
        report.setStatus("pending");

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(operator()));
        when(liveReportRepository.findById(reportId)).thenReturn(Optional.of(report));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.create(request, OPERATOR_ID));
        assertEquals("只能绑定已通过的直播报备", ex.getMessage());
    }

    @Test
    void create_rejectsHostMismatch() {
        String reportId = "11111111-1111-1111-1111-111111111111";
        CreateActivityRequest request = baseCreateRequest();
        request.setReportId(reportId);

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(operator()));
        when(liveReportRepository.findById(reportId)).thenReturn(Optional.of(passedReport(reportId, 20002L)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.create(request, OPERATOR_ID));
        assertEquals("已绑定直播报备不可修改", ex.getMessage());
    }

    @Test
    void delete_decrementsBoundReport() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        String reportId = "11111111-1111-1111-1111-111111111111";
        ActivityEntity entity = liveActivity(uuid, "on", "passed", 2);
        entity.setProvider("vhall");
        entity.setProviderRoomId("100");
        entity.setReportId(reportId);
        LiveReportEntity report = passedReport(reportId, OPERATOR_ID);
        report.setBoundLiveCount(2);

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(activityRepository.save(any(ActivityEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(liveReportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(liveReportRepository.save(any(LiveReportEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        activityService.delete(uuid);
        assertEquals(1, report.getBoundLiveCount());
        verify(liveReportRepository).save(report);
    }

    @Test
    void create_rejectsUnknownReport() {
        String reportId = "11111111-1111-1111-1111-111111111111";
        CreateActivityRequest request = baseCreateRequest();
        request.setReportId(reportId);

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(operator()));
        when(liveReportRepository.findById(reportId)).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.create(request, OPERATOR_ID));
        assertEquals("直播报备不存在", ex.getMessage());
    }

    @Test
    void update_rejectsHostChangeWhenBound() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        String reportId = "11111111-1111-1111-1111-111111111111";
        ActivityEntity entity = liveActivity(uuid, "on", "passed", 2);
        entity.setReportId(reportId);
        UpdateActivityRequest request = new UpdateActivityRequest();
        request.setHostIds(List.of(20002L));
        SysUserAccountEntity other = new SysUserAccountEntity();
        other.setId(20002L);
        other.setRsStatus(1);

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(other));
        when(liveReportRepository.findById(reportId)).thenReturn(Optional.of(passedReport(reportId, OPERATOR_ID)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.update(uuid, request, OPERATOR_ID));
        assertEquals("已绑定直播报备不可修改", ex.getMessage());
        verify(memberRepository, never()).deleteByActivityIdAndRole(any(), any(Integer.class));
        verify(auditRepository, never()).save(any());
    }

    @Test
    void replaceMembers_rejectsHostMismatchWhenBound() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        String reportId = "11111111-1111-1111-1111-111111111111";
        ActivityEntity entity = liveActivity(uuid, "on", "passed", 2);
        entity.setReportId(reportId);
        UpdateActivityMembersRequest request = new UpdateActivityMembersRequest();
        UpdateActivityMembersRequest.MemberBinding host = new UpdateActivityMembersRequest.MemberBinding();
        host.setId(20002L);
        host.setRole(ActivityMemberRole.HOST.getCode());
        request.setMembers(List.of(host));

        SysUserAccountEntity other = new SysUserAccountEntity();
        other.setId(20002L);
        other.setUserName("他人");
        other.setRsStatus(1);

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(other));
        when(liveReportRepository.findById(reportId)).thenReturn(Optional.of(passedReport(reportId, OPERATOR_ID)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.replaceMembers(uuid, request));
        assertEquals("已绑定直播报备不可修改", ex.getMessage());
        verify(memberRepository, never()).deleteByIdActivityId(any());
        verify(memberRepository, never()).saveAll(any());
    }

    @Test
    void replaceMembers_requiresHost() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "on", "passed", 2);
        UpdateActivityMembersRequest request = new UpdateActivityMembersRequest();
        UpdateActivityMembersRequest.MemberBinding assistant = new UpdateActivityMembersRequest.MemberBinding();
        assistant.setId(OPERATOR_ID);
        assistant.setRole(ActivityMemberRole.ASSISTANT.getCode());
        request.setMembers(List.of(assistant));

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.replaceMembers(uuid, request));
        assertEquals("主播至少选择1人", ex.getMessage());
        verify(memberRepository, never()).deleteByIdActivityId(any());
    }

    @Test
    void replaceMembers_rejectsInvalidRole() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "on", "passed", 2);
        UpdateActivityMembersRequest request = new UpdateActivityMembersRequest();
        UpdateActivityMembersRequest.MemberBinding bad = new UpdateActivityMembersRequest.MemberBinding();
        bad.setId(OPERATOR_ID);
        bad.setRole(9);
        request.setMembers(List.of(bad));

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.replaceMembers(uuid, request));
        assertEquals("开播角色无效", ex.getMessage());
    }

    @Test
    void replaceMembers_rejectsDuplicateUser() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "on", "passed", 2);
        UpdateActivityMembersRequest request = new UpdateActivityMembersRequest();
        UpdateActivityMembersRequest.MemberBinding host = new UpdateActivityMembersRequest.MemberBinding();
        host.setId(OPERATOR_ID);
        host.setRole(ActivityMemberRole.HOST.getCode());
        UpdateActivityMembersRequest.MemberBinding again = new UpdateActivityMembersRequest.MemberBinding();
        again.setId(OPERATOR_ID);
        again.setRole(ActivityMemberRole.ASSISTANT.getCode());
        request.setMembers(List.of(host, again));

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.replaceMembers(uuid, request));
        assertEquals("同一员工不能重复担任开播角色", ex.getMessage());
    }

    @Test
    void replaceMembers_savesHostAndAssistant() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "on", "passed", 2);
        UpdateActivityMembersRequest request = new UpdateActivityMembersRequest();
        UpdateActivityMembersRequest.MemberBinding host = new UpdateActivityMembersRequest.MemberBinding();
        host.setId(OPERATOR_ID);
        host.setRole(ActivityMemberRole.HOST.getCode());
        host.setNickname("主播昵称");
        host.setTag("讲师");
        UpdateActivityMembersRequest.MemberBinding assistant = new UpdateActivityMembersRequest.MemberBinding();
        assistant.setId(20002L);
        assistant.setRole(ActivityMemberRole.ASSISTANT.getCode());
        assistant.setTag("助教");
        request.setMembers(List.of(host, assistant));

        SysUserAccountEntity other = new SysUserAccountEntity();
        other.setId(20002L);
        other.setUserName("助理甲");
        other.setRsStatus(1);

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(operator(), other));
        when(memberRepository.findByActivityIds(anyCollection())).thenReturn(List.of());

        activityService.replaceMembers(uuid, request);

        verify(memberRepository).deleteByIdActivityId(uuid);
        ArgumentCaptor<List<ActivityMemberEntity>> captor = ArgumentCaptor.forClass(List.class);
        verify(memberRepository).saveAll(captor.capture());
        List<ActivityMemberEntity> saved = captor.getValue();
        assertEquals(2, saved.size());
        assertEquals(ActivityMemberRole.HOST.getCode(), saved.get(0).getRole());
        assertEquals("主播昵称", saved.get(0).getNickname());
        assertEquals(ActivityMemberRole.ASSISTANT.getCode(), saved.get(1).getRole());
        assertEquals(20002L, saved.get(1).getId().getUserId());
    }

    @Test
    void acceptMemberInvite_insertsAssistant() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "on", "passed", 2);
        AcceptActivityMemberRequest request = new AcceptActivityMemberRequest();
        request.setRole(ActivityMemberRole.ASSISTANT.getCode());
        request.setInviterId(20002L);

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(userRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(operator()));
        when(userRepository.findById(20002L)).thenReturn(Optional.of(activeUser(20002L, "邀约人")));
        when(memberRepository.findByActivityIds(anyCollection())).thenReturn(List.of());

        AcceptActivityMemberResponse response = activityService.acceptMemberInvite(uuid, OPERATOR_ID, request);

        assertFalse(response.isAlreadyMember());
        assertEquals(OPERATOR_ID, response.getId());
        assertEquals(ActivityMemberRole.ASSISTANT.getCode(), response.getRole());
        assertEquals("邀约人", response.getInviterName());
        ArgumentCaptor<ActivityMemberEntity> captor = ArgumentCaptor.forClass(ActivityMemberEntity.class);
        verify(memberRepository).save(captor.capture());
        assertEquals(uuid, captor.getValue().getId().getActivityId());
        assertEquals(OPERATOR_ID, captor.getValue().getId().getUserId());
        assertEquals(ActivityMemberRole.ASSISTANT.getCode(), captor.getValue().getRole());
        assertEquals("助教", captor.getValue().getTag());
        assertEquals(0, captor.getValue().getSortOrder());
    }

    @Test
    void acceptMemberInvite_sameRoleDoesNotInsertAgain() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "on", "passed", 2);
        ActivityMemberEntity existing = member(uuid, OPERATOR_ID, ActivityMemberRole.GUEST.getCode(), 0);
        AcceptActivityMemberRequest request = new AcceptActivityMemberRequest();
        request.setRole(ActivityMemberRole.GUEST.getCode());
        request.setInviterId(20002L);

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(userRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(operator()));
        when(userRepository.findById(20002L)).thenReturn(Optional.of(activeUser(20002L, "邀约人")));
        when(memberRepository.findByActivityIds(anyCollection())).thenReturn(List.of(existing));

        AcceptActivityMemberResponse response = activityService.acceptMemberInvite(uuid, OPERATOR_ID, request);

        assertTrue(response.isAlreadyMember());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void acceptMemberInvite_rejectsSwitchingRole() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "on", "passed", 2);
        AcceptActivityMemberRequest request = new AcceptActivityMemberRequest();
        request.setRole(ActivityMemberRole.HOST.getCode());
        request.setInviterId(20002L);

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(userRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(operator()));
        when(userRepository.findById(20002L)).thenReturn(Optional.of(activeUser(20002L, "邀约人")));
        when(memberRepository.findByActivityIds(anyCollection()))
                .thenReturn(List.of(member(uuid, OPERATOR_ID, ActivityMemberRole.ASSISTANT.getCode(), 0)));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.acceptMemberInvite(uuid, OPERATOR_ID, request));
        assertEquals("你已是本场的助理，不能再加入为主播", ex.getMessage());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void acceptMemberInvite_rejectsHostWhenReportBound() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "on", "passed", 2);
        entity.setReportId("report-1");
        AcceptActivityMemberRequest request = new AcceptActivityMemberRequest();
        request.setRole(ActivityMemberRole.HOST.getCode());
        request.setInviterId(20002L);

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(userRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(operator()));
        when(userRepository.findById(20002L)).thenReturn(Optional.of(activeUser(20002L, "邀约人")));
        when(memberRepository.findByActivityIds(anyCollection())).thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.acceptMemberInvite(uuid, OPERATOR_ID, request));
        assertEquals("已绑定直播报备，主播不能通过邀约加入", ex.getMessage());
    }

    @Test
    void acceptMemberInvite_rejectsInactiveInviter() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "on", "passed", 2);
        SysUserAccountEntity inviter = activeUser(20002L, "邀约人");
        inviter.setRsStatus(0);
        AcceptActivityMemberRequest request = new AcceptActivityMemberRequest();
        request.setRole(ActivityMemberRole.GUEST.getCode());
        request.setInviterId(20002L);

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(userRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(operator()));
        when(userRepository.findById(20002L)).thenReturn(Optional.of(inviter));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.acceptMemberInvite(uuid, OPERATOR_ID, request));
        assertEquals("邀约人无效", ex.getMessage());
        verify(memberRepository, never()).save(any());
    }

    @Test
    void update_hostIdsDoesNotWipeOtherRoles() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "on", "passed", 2);
        UpdateActivityRequest request = new UpdateActivityRequest();
        request.setHostIds(List.of(OPERATOR_ID));

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(activityRepository.save(any(ActivityEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findAllById(anyCollection())).thenReturn(List.of(operator()));
        when(userRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(operator()));

        activityService.update(uuid, request, OPERATOR_ID);

        verify(memberRepository).deleteByActivityIdAndRole(uuid, ActivityMemberRole.HOST.getCode());
        verify(memberRepository, never()).deleteByIdActivityId(any());
        verify(memberRepository).saveAll(any());
    }

    @Test
    void update_fromPassedAppendsSubmit() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "on", "passed", 2);
        UpdateActivityRequest request = new UpdateActivityRequest();
        request.setAutoList(true);

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(activityRepository.save(any(ActivityEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(operator()));

        activityService.update(uuid, request, OPERATOR_ID);

        assertEquals("pending", entity.getRiskStatus());
        assertEquals("on", entity.getShelfStatus());
        ArgumentCaptor<ActivityAuditEntity> row = ArgumentCaptor.forClass(ActivityAuditEntity.class);
        verify(auditRepository).save(row.capture());
        assertEquals(uuid, row.getValue().getActivityId());
        assertEquals("submit", row.getValue().getAction());
        assertNull(row.getValue().getResult());
        assertNull(row.getValue().getReason());
        assertEquals(OPERATOR_ID, row.getValue().getOperatorId());
        assertEquals("余灵巧", row.getValue().getOperatorName());
    }

    @Test
    void update_fromRejectedAppendsSubmit() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "banned", "rejected", 2);
        UpdateActivityRequest request = new UpdateActivityRequest();
        request.setDetailHtml("<p>改详情</p>");

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(activityRepository.save(any(ActivityEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.findById(OPERATOR_ID)).thenReturn(Optional.of(operator()));

        activityService.update(uuid, request, OPERATOR_ID);

        assertEquals("pending", entity.getRiskStatus());
        ArgumentCaptor<ActivityAuditEntity> row = ArgumentCaptor.forClass(ActivityAuditEntity.class);
        verify(auditRepository).save(row.capture());
        assertEquals("submit", row.getValue().getAction());
        assertEquals(OPERATOR_ID, row.getValue().getOperatorId());
    }

    @Test
    void update_pendingDoesNotAppendSubmit() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "pending", "pending", 2);
        UpdateActivityRequest request = new UpdateActivityRequest();
        request.setAutoList(false);

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.update(uuid, request, OPERATOR_ID));
        assertEquals("审核中不可编辑", ex.getMessage());
        assertEquals("pending", entity.getRiskStatus());
        verify(auditRepository, never()).save(any());
        verify(activityRepository, never()).save(any());
    }

    @Test
    void listRecords_mapsProviderRecords() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = new ActivityEntity();
        entity.setId(uuid);
        entity.setProvider("vhall");
        entity.setProviderRoomId("666781572");

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.listRecords("666781572")).thenReturn(new ProviderRecordList(
                1,
                List.of(new ProviderRecordItem(
                        "799703", "回放A", "01:02:03", "2026-09-01 10:00:00", "https://cover"))
        ));

        ActivityRecordListResponse response = activityService.listRecords(uuid);
        assertEquals(1, response.getTotal());
        assertEquals(1, response.getList().size());
        assertEquals("799703", response.getList().get(0).getId());
        assertEquals("回放A", response.getList().get(0).getTitle());
        assertEquals("https://cover", response.getList().get(0).getCoverUrl());
    }

    @Test
    void pushStream_mapsThreeFields() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = new ActivityEntity();
        entity.setId(uuid);
        entity.setProvider("vhall");
        entity.setProviderRoomId("752172335");

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.getPushStream("752172335")).thenReturn(new PushStreamInfo(
                "rtmp://rslive.vhall.com:1935/vhallyun/",
                "lss_ede878a9?vhost=thirdVhost&token=abc",
                "2026-02-15 00:00:01"
        ));

        ActivityPushStreamResponse response = activityService.pushStream(uuid);
        assertEquals("rtmp://rslive.vhall.com:1935/vhallyun/", response.getStreamAddress());
        assertEquals("lss_ede878a9?vhost=thirdVhost&token=abc", response.getStreamCode());
        assertEquals("2026-02-15 00:00:01", response.getExpireTime());
    }

    @Test
    void pushStream_propagatesVhallFailure() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = new ActivityEntity();
        entity.setId(uuid);
        entity.setProvider("vhall");
        entity.setProviderRoomId("752172335");

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.getPushStream("752172335"))
                .thenThrow(new VhallException(600, "rid-err", "参数错误"));

        VhallException ex = assertThrows(VhallException.class, () -> activityService.pushStream(uuid));
        assertEquals(600, ex.getCode());
        assertEquals("参数错误", ex.getMessage());
    }

    @Test
    void streamStatus_mapsStatus() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = new ActivityEntity();
        entity.setId(uuid);
        entity.setProvider("vhall");
        entity.setProviderRoomId("752172335");

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.getStreamStatus("752172335")).thenReturn(1);

        ActivityStreamStatusResponse response = activityService.streamStatus(uuid);
        assertEquals(1, response.getStatus());
    }

    @Test
    void streamStatus_propagatesVhallFailure() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = new ActivityEntity();
        entity.setId(uuid);
        entity.setProvider("vhall");
        entity.setProviderRoomId("752172335");

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.getStreamStatus("752172335"))
                .thenThrow(new VhallException(600, "rid-err", "参数错误"));

        VhallException ex = assertThrows(VhallException.class, () -> activityService.streamStatus(uuid));
        assertEquals(600, ex.getCode());
        assertEquals("参数错误", ex.getMessage());
    }

    @Test
    void startLive_writesStateFromProvider() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = new ActivityEntity();
        entity.setId(uuid);
        entity.setProvider("vhall");
        entity.setProviderRoomId("752172335");
        entity.setState(2);

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.start("752172335")).thenReturn(1);
        when(activityRepository.save(any(ActivityEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        StartActivityResponse response = activityService.startLive(uuid);
        assertEquals(uuid, response.getId());
        assertEquals(1, response.getState());
        assertEquals(1, entity.getState());
        verify(liveProvider).start("752172335");
    }

    @Test
    void startLive_propagatesVhallFailure() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = new ActivityEntity();
        entity.setId(uuid);
        entity.setProvider("vhall");
        entity.setProviderRoomId("752172335");

        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.start("752172335"))
                .thenThrow(new VhallException(600, "rid-err", "参数错误"));

        VhallException ex = assertThrows(VhallException.class, () -> activityService.startLive(uuid));
        assertEquals(600, ex.getCode());
        assertEquals("参数错误", ex.getMessage());
        verify(activityRepository, never()).save(any());
    }

    @Test
    void list_rejectsPageBelowOne() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.list(0, 20, null, null, null, null, null, null));
        assertEquals("page 不能小于 1", ex.getMessage());
    }

    @Test
    void audit_rejectBansShelf() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "on", "pending", 2);
        SysUserAccountEntity operator = operator();
        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(userRepository.findById(10001L)).thenReturn(Optional.of(operator));
        when(activityRepository.save(any(ActivityEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditRequest request = new AuditRequest();
        request.setResult("rejected");
        request.setReason("  封面不合规  ");

        AuditResultResponse response = activityService.audit(uuid, request, 10001L);
        assertEquals("rejected", response.getRiskStatus());
        assertEquals("banned", response.getShelfStatus());
        assertEquals("banned", entity.getShelfStatus());

        ArgumentCaptor<ActivityAuditEntity> row = ArgumentCaptor.forClass(ActivityAuditEntity.class);
        verify(auditRepository).save(row.capture());
        assertEquals("audit", row.getValue().getAction());
        assertEquals("rejected", row.getValue().getResult());
        assertEquals("封面不合规", row.getValue().getReason());
        assertEquals(10001L, row.getValue().getOperatorId());
        assertEquals("余灵巧", row.getValue().getOperatorName());
    }

    @Test
    void audit_passWithAutoListGoesOn() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "banned", "rejected", 2);
        entity.setAutoList(true);
        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(userRepository.findById(10001L)).thenReturn(Optional.of(operator()));
        when(activityRepository.save(any(ActivityEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditRequest request = new AuditRequest();
        request.setResult("passed");

        AuditResultResponse response = activityService.audit(uuid, request, 10001L);
        assertEquals("passed", response.getRiskStatus());
        assertEquals("on", response.getShelfStatus());
    }

    @Test
    void audit_passAfterBanWithoutAutoListReturnsToPending() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "banned", "rejected", 2);
        entity.setAutoList(false);
        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(userRepository.findById(10001L)).thenReturn(Optional.of(operator()));
        when(activityRepository.save(any(ActivityEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        AuditRequest request = new AuditRequest();
        request.setResult("passed");

        AuditResultResponse response = activityService.audit(uuid, request, 10001L);
        assertEquals("passed", response.getRiskStatus());
        assertEquals("pending", response.getShelfStatus());
        assertEquals(false, BroadcastRules.canBroadcast(
                entity.getRiskStatus(), entity.getShelfStatus(), entity.getState()));

        ArgumentCaptor<ActivityAuditEntity> row = ArgumentCaptor.forClass(ActivityAuditEntity.class);
        verify(auditRepository).save(row.capture());
        assertEquals("audit", row.getValue().getAction());
        assertEquals("passed", row.getValue().getResult());
        assertNull(row.getValue().getReason());
    }

    @Test
    void audits_returnsSubmitThenAuditNewestFirst() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "on", "passed", 2);
        entity.setCreatedTime(LocalDateTime.of(2026, 10, 14, 10, 0, 0));
        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));

        ActivityAuditEntity submit = new ActivityAuditEntity();
        submit.setId("s1");
        submit.setActivityId(uuid);
        submit.setAction("submit");
        submit.setOperatorId(10001L);
        submit.setOperatorName("余灵巧");
        submit.setCreatedTime(LocalDateTime.of(2026, 10, 14, 10, 0, 0));

        ActivityAuditEntity pass = new ActivityAuditEntity();
        pass.setId("a1");
        pass.setActivityId(uuid);
        pass.setAction("audit");
        pass.setResult("passed");
        pass.setOperatorId(10001L);
        pass.setOperatorName("余灵巧");
        pass.setCreatedTime(LocalDateTime.of(2026, 10, 14, 11, 0, 0));

        when(auditRepository.findByActivityIdOrderByCreatedTimeDesc(uuid)).thenReturn(List.of(pass, submit));

        AuditListResponse response = activityService.audits(uuid);
        assertEquals(2, response.getList().size());
        assertEquals("audit", response.getList().get(0).getAction());
        assertEquals("passed", response.getList().get(0).getResult());
        assertEquals("2026-10-14 11:00:00", response.getList().get(0).getCreatedTime());
        assertEquals("submit", response.getList().get(1).getAction());
        assertNull(response.getList().get(1).getResult());
        assertEquals("余灵巧", response.getList().get(1).getOperatorName());
        assertEquals("2026-10-14 10:00:00", response.getList().get(1).getCreatedTime());
    }

    @Test
    void audits_synthesizesSubmitWhenHistoryMissing() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "banned", "rejected", 2);
        entity.setCreatedTime(LocalDateTime.of(2026, 10, 1, 9, 30, 0));
        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));

        ActivityAuditEntity reject = new ActivityAuditEntity();
        reject.setId("a1");
        reject.setActivityId(uuid);
        reject.setAction("audit");
        reject.setResult("rejected");
        reject.setReason("封面不合规");
        reject.setOperatorId(10001L);
        reject.setOperatorName("余灵巧");
        reject.setCreatedTime(LocalDateTime.of(2026, 10, 1, 10, 0, 0));
        when(auditRepository.findByActivityIdOrderByCreatedTimeDesc(uuid)).thenReturn(List.of(reject));

        AuditListResponse response = activityService.audits(uuid);
        assertEquals(2, response.getList().size());
        assertEquals("audit", response.getList().get(0).getAction());
        assertEquals("rejected", response.getList().get(0).getResult());
        assertEquals("封面不合规", response.getList().get(0).getReason());
        assertEquals("submit", response.getList().get(1).getAction());
        assertEquals("synthetic-submit-" + uuid, response.getList().get(1).getId());
        assertEquals("系统", response.getList().get(1).getOperatorName());
        assertEquals(0L, response.getList().get(1).getOperatorId());
        assertEquals("2026-10-01 09:30:00", response.getList().get(1).getCreatedTime());
        assertNull(response.getList().get(1).getResult());
    }

    @Test
    void audits_synthesizesSubmitWhenNoRows() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "pending", "pending", 2);
        entity.setCreatedTime(LocalDateTime.of(2026, 10, 17, 8, 0, 0));
        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(auditRepository.findByActivityIdOrderByCreatedTimeDesc(uuid)).thenReturn(List.of());

        AuditListResponse response = activityService.audits(uuid);
        assertEquals(1, response.getList().size());
        assertEquals("submit", response.getList().get(0).getAction());
        assertEquals("synthetic-submit-" + uuid, response.getList().get(0).getId());
        assertEquals("系统", response.getList().get(0).getOperatorName());
        assertEquals(0L, response.getList().get(0).getOperatorId());
        assertEquals("2026-10-17 08:00:00", response.getList().get(0).getCreatedTime());
        assertNull(response.getList().get(0).getResult());
    }

    @Test
    void updatePlayback_requiresFieldsWhenEnabled() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "pending", "pending", 2);
        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));

        PlaybackUpdateRequest request = new PlaybackUpdateRequest();
        request.setPlaybackEnabled(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.updatePlayback(uuid, request));
        assertEquals("playbackValidityType 无效", ex.getMessage());
    }

    @Test
    void updateSales_paidRequiresPriceAndLinePriceGreater() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "pending", "pending", 2);
        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(activityRepository.save(any(ActivityEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SalesUpdateRequest badLine = new SalesUpdateRequest();
        badLine.setSellAlone(true);
        badLine.setSellType("paid");
        badLine.setPrice("9.90");
        badLine.setLinePrice("9.90");
        badLine.setAccessValidityType("long");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.updateSales(uuid, badLine));
        assertEquals("划线价格须大于商品价格", ex.getMessage());

        SalesUpdateRequest ok = new SalesUpdateRequest();
        ok.setSellAlone(true);
        ok.setSellType("paid");
        ok.setPrice("9.90");
        ok.setLinePrice("19.90");
        ok.setAccessValidityType("days");
        ok.setAccessValidityDays(30);
        SalesUpdateResponse response = activityService.updateSales(uuid, ok);
        assertEquals("paid", response.getSellType());
        assertEquals("30天有效", response.getValidityLabel());
        assertEquals("30天有效", entity.getValidityLabel());
    }

    @Test
    void updateSales_encryptedRequiresSixDigits() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "pending", "pending", 2);
        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(activityRepository.save(any(ActivityEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(sellPasswordCrypto.encrypt("123456")).thenReturn("cipher");

        SalesUpdateRequest bad = new SalesUpdateRequest();
        bad.setSellAlone(true);
        bad.setSellType("encrypted");
        bad.setPassword("12ab56");
        bad.setAccessValidityType("long");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.updateSales(uuid, bad));
        assertEquals("密码须为恰好6位数字", ex.getMessage());

        SalesUpdateRequest ok = new SalesUpdateRequest();
        ok.setSellAlone(true);
        ok.setSellType("encrypted");
        ok.setPassword("123456");
        ok.setAccessValidityType("long");
        activityService.updateSales(uuid, ok);
        assertEquals("cipher", entity.getSellPasswordCipher());
        verify(sellPasswordCrypto).encrypt("123456");
    }

    @Test
    void delete_softDeletesAndListHidesIt() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "on", "passed", 2);
        entity.setProvider("vhall");
        entity.setProviderRoomId("100");
        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(activityRepository.save(any(ActivityEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        DeleteActivityResponse deleted = activityService.delete(uuid);
        assertEquals(uuid, deleted.getId());
        assertNotNull(entity.getDeletedTime());
        verify(liveProvider).delete("100");
        verify(activityRepository, never()).delete(any());

        ActivityEntity alive = liveActivity("b1b2c3d4-e5f6-7890-abcd-ef1234567890", "on", "passed", 2);
        when(activityRepository.search(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(alive, entity), PageRequest.of(0, 20), 2));

        ActivityListResponse list = activityService.list(1, 20, null, null, null, null, null, null);
        assertEquals(1, list.getList().size());
        assertEquals(alive.getId(), list.getList().get(0).getId());
        assertTrue(list.getList().stream().noneMatch(item -> uuid.equals(item.getId())));
    }

    @Test
    void shelf_bannedCannotChange() {
        String uuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890";
        ActivityEntity entity = liveActivity(uuid, "banned", "rejected", 2);
        when(activityRepository.findById(uuid)).thenReturn(Optional.of(entity));
        ShelfRequest request = new ShelfRequest();
        request.setListed(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.shelf(uuid, request));
        assertEquals("强制封禁不可上下架", ex.getMessage());
        verify(activityRepository, never()).save(any());
    }

    private static LiveReportEntity passedReport(String id, long hostId) {
        LiveReportEntity report = new LiveReportEntity();
        report.setId(id);
        report.setTitle("报备");
        report.setStatus("passed");
        report.setHostId(hostId);
        report.setBoundLiveCount(0);
        return report;
    }

    private static CreateActivityRequest baseCreateRequest() {
        CreateActivityRequest request = new CreateActivityRequest();
        request.setProvider("vhall");
        request.setTitle("活动");
        request.setStartTime("2026-10-01 20:00:00");
        request.setType(2);
        request.setAutoList(false);
        request.setHostIds(List.of(10001L));
        return request;
    }

    private static ActivityEntity liveActivity(String id, String shelf, String risk, int state) {
        ActivityEntity entity = new ActivityEntity();
        entity.setId(id);
        entity.setProvider("vhall");
        entity.setShelfStatus(shelf);
        entity.setRiskStatus(risk);
        entity.setSellType("free");
        entity.setSellAlone(true);
        entity.setAccessValidityType("long");
        entity.setValidityLabel("长期有效");
        entity.setAutoList(false);
        entity.setPlaybackEnabled(true);
        entity.setPlaybackValidityType("forever");
        entity.setPlaybackSpeedAllow(true);
        entity.setPlaybackFastForwardAllow(true);
        entity.setState(state);
        entity.setTitle("活动");
        return entity;
    }

    private static SysUserAccountEntity operator() {
        return activeUser(OPERATOR_ID, "余灵巧");
    }

    private static SysUserAccountEntity activeUser(long id, String name) {
        SysUserAccountEntity user = new SysUserAccountEntity();
        user.setId(id);
        user.setRsStatus(1);
        user.setUserName(name);
        return user;
    }

    private static ActivityMemberEntity member(String activityId, long userId, int role, int sortOrder) {
        ActivityMemberEntity row = new ActivityMemberEntity();
        row.setId(new ActivityMemberId(activityId, userId));
        row.setRole(role);
        row.setSortOrder(sortOrder);
        return row;
    }

    @SuppressWarnings("unused")
    private static MaterialEntity material(String id) {
        MaterialEntity entity = new MaterialEntity();
        entity.setId(id);
        entity.setName("课件");
        entity.setUrl("https://x");
        entity.setType("document");
        entity.setGroupId("g1");
        entity.setObjectKey("k1");
        return entity;
    }
}
