package com.zhibo.admin.activity;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.admin.activity.dto.ActivityRecordListResponse;
import com.zhibo.admin.activity.dto.CreateActivityRequest;
import com.zhibo.admin.activity.dto.CreateActivityResponse;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.ProviderNotAvailableException;
import com.zhibo.liveprovider.dto.CreateRoomCommand;
import com.zhibo.liveprovider.dto.CreateRoomResult;
import com.zhibo.liveprovider.dto.ProviderRecordItem;
import com.zhibo.liveprovider.dto.ProviderRecordList;
import com.zhibo.liveprovider.dto.ProviderRoomSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @InjectMocks
    private ActivityService activityService;

    @Test
    void create_persistsAndReturnsUuid() {
        CreateActivityRequest request = new CreateActivityRequest();
        request.setProvider("vhall");
        request.setTitle("  测试活动  ");
        request.setStartTime("2026-10-01 20:00:00");
        request.setEndTime("2026-10-01 22:00:00");
        request.setIntroduction("  一场简介  ");
        request.setType(2);
        request.setUltraLowLatency(true);

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
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
    }

    @Test
    void create_type4_requiresRecordId() {
        CreateActivityRequest request = new CreateActivityRequest();
        request.setProvider("vhall");
        request.setTitle("录播");
        request.setStartTime("2026-10-01 20:00:00");
        request.setIntroduction("简介");
        request.setType(4);

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.create(any(CreateRoomCommand.class)))
                .thenThrow(new IllegalArgumentException("录播须填写 recordId"));

        assertThrows(IllegalArgumentException.class, () -> activityService.create(request));
    }

    @Test
    void create_rejectsUnknownProvider() {
        CreateActivityRequest request = new CreateActivityRequest();
        request.setProvider("aliyun");
        request.setTitle("活动");
        request.setStartTime("2026-10-01 20:00:00");
        request.setType(2);

        when(providerRegistry.require("aliyun")).thenThrow(new ProviderNotAvailableException());

        assertThrows(ProviderNotAvailableException.class, () -> activityService.create(request));
    }

    @Test
    void create_rejectsEndTimeNotAfterStart() {
        CreateActivityRequest request = new CreateActivityRequest();
        request.setProvider("vhall");
        request.setTitle("活动");
        request.setStartTime("2026-10-01 20:00:00");
        request.setEndTime("2026-10-01 20:00:00");
        request.setType(1);

        when(providerRegistry.require("vhall")).thenReturn(liveProvider);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> activityService.create(request));
        assertEquals("结束时间须晚于开始时间", ex.getMessage());
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
}
