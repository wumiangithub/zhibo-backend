package com.zhibo.live.callback;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRecordEntity;
import com.zhibo.activity.ActivityRecordRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.VhallRoomIdResolver;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.ProviderRecordDownload;
import com.zhibo.liveprovider.dto.ProviderRecordInfo;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.util.VhallCallbackSignUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VhallRecordGenerateCallbackServiceTest {

    private static final String SECRET = "callback-secret";
    private static final String WEBINAR_ID = "123456";
    private static final String ACTIVITY_ID = "act-uuid-1";
    private static final long RECORD_ID = 799703L;

    @Mock
    private VhallProperties vhallProperties;
    @Mock
    private VhallRoomIdResolver roomIdResolver;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private ActivityRecordRepository activityRecordRepository;
    @Mock
    private LiveProviderRegistry providerRegistry;
    @Mock
    private LiveProvider liveProvider;

    private VhallRecordGenerateCallbackService service;

    @BeforeEach
    void setUp() {
        service = new VhallRecordGenerateCallbackService(
                vhallProperties,
                roomIdResolver,
                activityRepository,
                activityRecordRepository,
                providerRegistry);
        lenient().when(vhallProperties.resolveCallbackSecret()).thenReturn(SECRET);
        lenient().when(providerRegistry.require("vhall")).thenReturn(liveProvider);
    }

    @Test
    void handle_statusOk_insertsPendingPackageThenPackaging() {
        ActivityEntity activity = activity();
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
        when(activityRecordRepository.findByActivityIdAndProviderAndRecordId(
                ACTIVITY_ID, "vhall", RECORD_ID)).thenReturn(Optional.empty());
        when(liveProvider.getRecordInfo(RECORD_ID)).thenReturn(new ProviderRecordInfo(
                RECORD_ID,
                Long.parseLong(WEBINAR_ID),
                "回放A",
                3723,
                "https://cover.example/a.png",
                2048L,
                "2026-09-01 10:00:00"));
        java.util.ArrayList<Integer> savedStatuses = new java.util.ArrayList<>();
        when(activityRecordRepository.save(any(ActivityRecordEntity.class)))
                .thenAnswer(inv -> {
                    ActivityRecordEntity e = inv.getArgument(0);
                    savedStatuses.add(e.getStatus());
                    return e;
                });

        Map<String, Object> body = generateBody(VhallRecordGenerateCallbackService.STATUS_OK);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallRecordGenerateCallbackService.HandleResult.OK, service.handle(body));

        ArgumentCaptor<ActivityRecordEntity> cap = ArgumentCaptor.forClass(ActivityRecordEntity.class);
        verify(activityRecordRepository, org.mockito.Mockito.times(2)).save(cap.capture());
        ActivityRecordEntity first = cap.getAllValues().get(0);
        assertEquals(ACTIVITY_ID, first.getActivityId());
        assertEquals("vhall", first.getProvider());
        assertEquals(RECORD_ID, first.getRecordId());
        assertEquals("回放A", first.getTitle());
        assertEquals(3723, first.getDurationSec());
        assertEquals("https://cover.example/a.png", first.getCoverUrl());
        assertEquals(2048L, first.getStorageKb());
        assertEquals(java.util.List.of(
                ActivityRecordEntity.STATUS_PENDING_PACKAGE,
                ActivityRecordEntity.STATUS_PACKAGING), savedStatuses);
        assertEquals(LocalDateTime.of(2026, 9, 1, 10, 0, 0), first.getProviderCreatedTime());
        assertNull(first.getTosUrl());
        verify(liveProvider).submitRecordDownload(RECORD_ID);
        verify(liveProvider, never()).checkRecordDownload(anyLong());
    }

    @Test
    void handle_statusFail_skipsInsert() {
        Map<String, Object> body = generateBody(VhallRecordGenerateCallbackService.STATUS_FAIL);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallRecordGenerateCallbackService.HandleResult.OK, service.handle(body));
        verify(activityRecordRepository, never()).save(any());
        verify(liveProvider, never()).getRecordInfo(anyLong());
        verify(liveProvider, never()).submitRecordDownload(anyLong());
    }

    @Test
    void handle_existingReady_skipsSubmit() {
        ActivityEntity activity = activity();
        ActivityRecordEntity row = existingRow(ActivityRecordEntity.STATUS_READY);
        row.setTosUrl("https://cdn.example/a.mp4");
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
        when(activityRecordRepository.findByActivityIdAndProviderAndRecordId(
                ACTIVITY_ID, "vhall", RECORD_ID)).thenReturn(Optional.of(row));

        Map<String, Object> body = generateBody(VhallRecordGenerateCallbackService.STATUS_OK);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallRecordGenerateCallbackService.HandleResult.OK, service.handle(body));
        verify(liveProvider, never()).getRecordInfo(anyLong());
        verify(liveProvider, never()).checkRecordDownload(anyLong());
        verify(liveProvider, never()).submitRecordDownload(anyLong());
        verify(activityRecordRepository, never()).save(any());
        assertEquals("https://cdn.example/a.mp4", row.getTosUrl());
    }

    @Test
    void handle_existingPending_checkStatus0_marksPackaging() {
        // check 只查询；status=0 须显式 submit → packaging
        ActivityEntity activity = activity();
        ActivityRecordEntity row = existingRow(ActivityRecordEntity.STATUS_PENDING_PACKAGE);
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
        when(activityRecordRepository.findByActivityIdAndProviderAndRecordId(
                ACTIVITY_ID, "vhall", RECORD_ID)).thenReturn(Optional.of(row));
        when(liveProvider.checkRecordDownload(RECORD_ID))
                .thenReturn(new ProviderRecordDownload(RECORD_ID, null, 0, 0));
        when(activityRecordRepository.save(any(ActivityRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = generateBody(VhallRecordGenerateCallbackService.STATUS_OK);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallRecordGenerateCallbackService.HandleResult.OK, service.handle(body));
        verify(liveProvider).checkRecordDownload(RECORD_ID);
        verify(liveProvider).submitRecordDownload(RECORD_ID);
        assertEquals(ActivityRecordEntity.STATUS_PACKAGING, row.getStatus());
        verify(activityRecordRepository).save(row);
    }

    @Test
    void handle_existingPending_checkStatus1or2_marksPackaging() {
        ActivityEntity activity = activity();
        ActivityRecordEntity row = existingRow(ActivityRecordEntity.STATUS_PENDING_PACKAGE);
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
        when(activityRecordRepository.findByActivityIdAndProviderAndRecordId(
                ACTIVITY_ID, "vhall", RECORD_ID)).thenReturn(Optional.of(row));
        when(liveProvider.checkRecordDownload(RECORD_ID))
                .thenReturn(new ProviderRecordDownload(
                        RECORD_ID, "https://vod.example/a.zip", 1, 100));
        when(activityRecordRepository.save(any(ActivityRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = generateBody(VhallRecordGenerateCallbackService.STATUS_OK);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallRecordGenerateCallbackService.HandleResult.OK, service.handle(body));
        verify(liveProvider).checkRecordDownload(RECORD_ID);
        verify(liveProvider, never()).submitRecordDownload(anyLong());
        assertEquals(ActivityRecordEntity.STATUS_PACKAGING, row.getStatus());
    }

    @Test
    void handle_existingPending_checkStatus3_resubmitsPackaging() {
        ActivityEntity activity = activity();
        ActivityRecordEntity row = existingRow(ActivityRecordEntity.STATUS_FAILED);
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
        when(activityRecordRepository.findByActivityIdAndProviderAndRecordId(
                ACTIVITY_ID, "vhall", RECORD_ID)).thenReturn(Optional.of(row));
        when(liveProvider.checkRecordDownload(RECORD_ID))
                .thenReturn(new ProviderRecordDownload(RECORD_ID, null, 3, 0));
        when(activityRecordRepository.save(any(ActivityRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = generateBody(VhallRecordGenerateCallbackService.STATUS_OK);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallRecordGenerateCallbackService.HandleResult.OK, service.handle(body));
        verify(liveProvider).checkRecordDownload(RECORD_ID);
        verify(liveProvider).submitRecordDownload(RECORD_ID);
        assertEquals(ActivityRecordEntity.STATUS_PACKAGING, row.getStatus());
    }

    @Test
    void handle_getInfoFails_stillOkNoInsert() {
        ActivityEntity activity = activity();
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
        when(activityRecordRepository.findByActivityIdAndProviderAndRecordId(
                ACTIVITY_ID, "vhall", RECORD_ID)).thenReturn(Optional.empty());
        when(liveProvider.getRecordInfo(RECORD_ID)).thenThrow(new IllegalStateException("微吼超时"));

        Map<String, Object> body = generateBody(VhallRecordGenerateCallbackService.STATUS_OK);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallRecordGenerateCallbackService.HandleResult.OK, service.handle(body));
        verify(activityRecordRepository, never()).save(any());
        verify(liveProvider, never()).submitRecordDownload(anyLong());
    }

    @Test
    void handle_submitFailsAfterInsert_keepsPendingPackage() {
        ActivityEntity activity = activity();
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
        when(activityRecordRepository.findByActivityIdAndProviderAndRecordId(
                ACTIVITY_ID, "vhall", RECORD_ID)).thenReturn(Optional.empty());
        when(liveProvider.getRecordInfo(RECORD_ID)).thenReturn(new ProviderRecordInfo(
                RECORD_ID, null, "回放B", 10, null, 1L, null));
        when(activityRecordRepository.save(any(ActivityRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        org.mockito.Mockito.doThrow(new IllegalStateException("submit fail"))
                .when(liveProvider).submitRecordDownload(RECORD_ID);

        Map<String, Object> body = generateBody(VhallRecordGenerateCallbackService.STATUS_OK);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallRecordGenerateCallbackService.HandleResult.OK, service.handle(body));
        ArgumentCaptor<ActivityRecordEntity> cap = ArgumentCaptor.forClass(ActivityRecordEntity.class);
        verify(activityRecordRepository).save(cap.capture());
        assertEquals(ActivityRecordEntity.STATUS_PENDING_PACKAGE, cap.getValue().getStatus());
    }

    @Test
    void handle_badSignatureRejected() {
        Map<String, Object> body = generateBody(VhallRecordGenerateCallbackService.STATUS_OK);
        body.put("signature", "deadbeef");

        assertEquals(
                VhallRecordGenerateCallbackService.HandleResult.BAD_SIGNATURE,
                service.handle(body));
        verify(activityRecordRepository, never()).save(any());
    }

    @Test
    void handle_unknownWebinarStillOk() {
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.empty());
        Map<String, Object> body = generateBody(VhallRecordGenerateCallbackService.STATUS_OK);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallRecordGenerateCallbackService.HandleResult.OK, service.handle(body));
        verify(activityRecordRepository, never()).save(any());
        verify(liveProvider, never()).getRecordInfo(anyLong());
    }

    @Test
    void handle_softDeletedStillOkNoInsert() {
        ActivityEntity activity = activity();
        activity.setDeletedTime(LocalDateTime.of(2026, 10, 1, 12, 0));
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));

        Map<String, Object> body = generateBody(VhallRecordGenerateCallbackService.STATUS_OK);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallRecordGenerateCallbackService.HandleResult.OK, service.handle(body));
        verify(activityRecordRepository, never()).save(any());
        verify(liveProvider, never()).getRecordInfo(anyLong());
    }

    @Test
    void handle_wrongMethodIgnored() {
        Map<String, Object> body = generateBody(VhallRecordGenerateCallbackService.STATUS_OK);
        body.put("method_id", 6);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallRecordGenerateCallbackService.HandleResult.IGNORED, service.handle(body));
        verify(activityRecordRepository, never()).save(any());
        verify(liveProvider, never()).getRecordInfo(anyLong());
    }

    @Test
    void handle_missingRecordId_badRequest() {
        Map<String, Object> body = generateBody(VhallRecordGenerateCallbackService.STATUS_OK);
        body.remove("record_id");
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(
                VhallRecordGenerateCallbackService.HandleResult.BAD_REQUEST,
                service.handle(body));
        verify(roomIdResolver, never()).resolveRoomId(anyString());
    }

    private static ActivityEntity activity() {
        ActivityEntity e = new ActivityEntity();
        e.setId(ACTIVITY_ID);
        e.setProvider("vhall");
        e.setProviderRoomId(WEBINAR_ID);
        return e;
    }

    private static ActivityRecordEntity existingRow(int status) {
        ActivityRecordEntity e = new ActivityRecordEntity();
        e.setId(1L);
        e.setActivityId(ACTIVITY_ID);
        e.setProvider("vhall");
        e.setRecordId(RECORD_ID);
        e.setStatus(status);
        e.setTitle("旧标题");
        return e;
    }

    private static Map<String, Object> generateBody(int status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("method_id", VhallRecordGenerateCallbackService.METHOD_RECORD_GENERATE);
        body.put("refer", "vhall");
        body.put("time", 1_505_095_994L);
        body.put("webinar_id", Integer.parseInt(WEBINAR_ID));
        body.put("record_id", RECORD_ID);
        body.put("status", status);
        body.put("mid", "mid-1");
        return body;
    }
}
