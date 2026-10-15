package com.zhibo.admin.activity;

import com.zhibo.activity.ActivityAuditEntity;
import com.zhibo.activity.ActivityAuditRepository;
import com.zhibo.activity.ActivityCoursewareRepository;
import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityHostRepository;
import com.zhibo.activity.ActivityOwnerRepository;
import com.zhibo.activity.ActivityProductRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.activity.dto.ActivityListResponse;
import com.zhibo.admin.activity.dto.ActivityRecordListResponse;
import com.zhibo.admin.activity.dto.AuditRequest;
import com.zhibo.admin.activity.dto.AuditResultResponse;
import com.zhibo.admin.activity.dto.CreateActivityRequest;
import com.zhibo.admin.activity.dto.CreateActivityResponse;
import com.zhibo.admin.activity.dto.DeleteActivityResponse;
import com.zhibo.admin.activity.dto.PlaybackUpdateRequest;
import com.zhibo.admin.activity.dto.SalesUpdateRequest;
import com.zhibo.admin.activity.dto.SalesUpdateResponse;
import com.zhibo.admin.activity.dto.ShelfRequest;
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
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityServiceTest {

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
    private ActivityHostRepository hostRepository;
    @Mock
    private ActivityCoursewareRepository coursewareRepository;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private SysUserAccountRepository userRepository;
    @Mock
    private SellPasswordCryptoService sellPasswordCrypto;

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
        when(liveProvider.create(any(CreateRoomCommand.class)))
                .thenReturn(new CreateRoomResult("123456"));
        when(liveProvider.fetchRoom("123456")).thenReturn(new ProviderRoomSnapshot(
                "测试活动", 2, 2, 0, null, "https://share", "2026-10-01 20:00:00",
                null, "一场简介", 0, null));
        when(activityRepository.save(any(ActivityEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateActivityResponse response = activityService.create(request);

        ArgumentCaptor<CreateRoomCommand> cmd = ArgumentCaptor.forClass(CreateRoomCommand.class);
        verify(liveProvider).create(cmd.capture());
        assertEquals("测试活动", cmd.getValue().title());
        assertEquals(2, cmd.getValue().type());
        assertEquals("一场简介", cmd.getValue().introduction());
        assertEquals(true, cmd.getValue().ultraLowLatency());

        ArgumentCaptor<ActivityEntity> entity = ArgumentCaptor.forClass(ActivityEntity.class);
        verify(activityRepository).save(entity.capture());
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
        verify(hostRepository).deleteByIdActivityId(response.getId());
        verify(hostRepository).saveAll(any());
    }

    @Test
    void create_rejectsListed() {
        CreateActivityRequest request = baseCreateRequest();
        request.setListed(true);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.create(request));
        assertEquals("请改用 autoList，不再支持 listed", ex.getMessage());
    }

    @Test
    void create_requiresHost() {
        CreateActivityRequest request = baseCreateRequest();
        request.setHostIds(List.of());

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.create(request));
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
        when(liveProvider.create(any(CreateRoomCommand.class)))
                .thenThrow(new IllegalArgumentException("录播须填写 recordId"));

        assertThrows(IllegalArgumentException.class, () -> activityService.create(request));
    }

    @Test
    void create_rejectsUnknownProvider() {
        CreateActivityRequest request = baseCreateRequest();
        request.setProvider("aliyun");

        when(providerRegistry.require("aliyun")).thenThrow(new ProviderNotAvailableException());

        assertThrows(ProviderNotAvailableException.class, () -> activityService.create(request));
    }

    @Test
    void create_rejectsEndTimeNotAfterStart() {
        CreateActivityRequest request = baseCreateRequest();
        request.setEndTime("2026-10-01 20:00:00");

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.create(request));
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
                () -> activityService.create(request));
        assertEquals("课件无效", ex.getMessage());
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
        assertNull(row.getValue().getReason());
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
        assertNotNull(entity.getDeletedAt());
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

    private static CreateActivityRequest baseCreateRequest() {
        CreateActivityRequest request = new CreateActivityRequest();
        request.setProvider("vhall");
        request.setTitle("活动");
        request.setStartTime("2026-10-01 20:00:00");
        request.setType(2);
        request.setAutoList(false);
        request.setHostIds(List.of("10001"));
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
        SysUserAccountEntity user = new SysUserAccountEntity();
        user.setId(10001L);
        user.setRsStatus(1);
        user.setUserName("余灵巧");
        return user;
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
