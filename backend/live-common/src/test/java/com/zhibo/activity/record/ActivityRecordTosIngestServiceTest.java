package com.zhibo.activity.record;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.activity.ActivityEntity;
import com.zhibo.activity.ActivityRecordEntity;
import com.zhibo.activity.ActivityRecordRepository;
import com.zhibo.activity.ActivityRepository;
import com.zhibo.activity.VhallRoomIdResolver;
import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.ProviderRecordInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ActivityRecordTosIngestServiceTest {

    private static final String ACTIVITY_ID = "act-uuid-1";
    private static final String WEBINAR_ID = "123456";
    private static final long RECORD_ID = 799703L;
    private static final String DOWNLOAD_URL = "https://vod.example/a.zip";
    private static final String TOS_URL =
            "https://cdn.example/activity/record/vhall_act-uuid-1_799703.mp4";

    @Mock
    private ActivityRecordRepository activityRecordRepository;
    @Mock
    private ActivityRepository activityRepository;
    @Mock
    private VhallRoomIdResolver roomIdResolver;
    @Mock
    private LiveProviderRegistry providerRegistry;
    @Mock
    private LiveProvider liveProvider;
    @Mock
    private ActivityRecordTosClient tosClient;
    @Mock
    private ActivityRecordFileFetcher fileFetcher;

    private ActivityRecordTosIngestService service;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        service = new ActivityRecordTosIngestService(
                activityRecordRepository,
                activityRepository,
                roomIdResolver,
                providerRegistry,
                tosClient,
                fileFetcher);
    }

    @Test
    void inFlight_tryMark_recordsStartTime_andClears() {
        assertFalse(service.isIngestInFlight(RECORD_ID));
        assertTrue(service.tryMarkInFlight(RECORD_ID));
        assertTrue(service.isIngestInFlight(RECORD_ID));
        assertFalse(service.tryMarkInFlight(RECORD_ID));
        service.clearInFlight(RECORD_ID);
        assertFalse(service.isIngestInFlight(RECORD_ID));
    }

    @Test
    void inFlight_staleStartTime_clearedAsNotInFlight() {
        assertTrue(service.tryMarkInFlightAt(RECORD_ID, Instant.now().minus(45, ChronoUnit.MINUTES)));
        assertFalse(service.isIngestInFlight(RECORD_ID));
        assertTrue(service.tryMarkInFlight(RECORD_ID));
    }

    @Test
    void markFailed_updatesExistingRow() {
        ActivityRecordEntity row = existingRow(ActivityRecordEntity.STATUS_PENDING_PACKAGE);
        when(activityRecordRepository.findByRecordId(RECORD_ID)).thenReturn(List.of(row));
        when(activityRecordRepository.save(any(ActivityRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.markFailed(RECORD_ID);

        assertEquals(ActivityRecordEntity.STATUS_FAILED, row.getStatus());
        verify(activityRecordRepository).save(row);
    }

    @Test
    void markFailed_skipsReadyWithTosUrl() {
        ActivityRecordEntity row = existingRow(ActivityRecordEntity.STATUS_READY);
        row.setTosUrl(TOS_URL);
        when(activityRecordRepository.findByRecordId(RECORD_ID)).thenReturn(List.of(row));

        service.markFailed(RECORD_ID);

        assertEquals(ActivityRecordEntity.STATUS_READY, row.getStatus());
        verify(activityRecordRepository, never()).save(any());
    }

    @Test
    void ingestFromUrl_existing_marksDownloadingThenReady() throws Exception {
        ActivityRecordEntity row = existingRow(ActivityRecordEntity.STATUS_PACKAGING);
        when(activityRecordRepository.findByRecordId(RECORD_ID)).thenReturn(List.of(row));
        when(activityRecordRepository.save(any(ActivityRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Path mp4 = tempDir.resolve("video.mp4");
        Files.write(mp4, new byte[] {0, 0, 0, 0, 'f', 't', 'y', 'p'});
        when(fileFetcher.fetchMp4(eq(DOWNLOAD_URL), any(Path.class))).thenReturn(mp4);
        String objectKey = ActivityRecordTosIngestService.buildObjectKey("vhall", ACTIVITY_ID, RECORD_ID);
        when(tosClient.putFile(eq(objectKey), eq(mp4), eq("video/mp4"))).thenReturn(TOS_URL);

        service.ingestFromUrl(RECORD_ID, DOWNLOAD_URL);

        assertEquals(ActivityRecordEntity.STATUS_READY, row.getStatus());
        assertEquals(objectKey, row.getObjectKey());
        assertEquals(TOS_URL, row.getTosUrl());
        verify(fileFetcher).fetchMp4(eq(DOWNLOAD_URL), any(Path.class));
        verify(tosClient).putFile(objectKey, mp4, "video/mp4");
    }

    @Test
    void ingestFromUrl_existing_fetchFails_marksFailed() throws Exception {
        ActivityRecordEntity row = existingRow(ActivityRecordEntity.STATUS_PACKAGING);
        when(activityRecordRepository.findByRecordId(RECORD_ID)).thenReturn(List.of(row));
        when(activityRecordRepository.save(any(ActivityRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(fileFetcher.fetchMp4(eq(DOWNLOAD_URL), any(Path.class)))
                .thenThrow(new java.io.IOException("boom"));

        service.ingestFromUrl(RECORD_ID, DOWNLOAD_URL);

        assertEquals(ActivityRecordEntity.STATUS_FAILED, row.getStatus());
        assertNull(row.getTosUrl());
        verify(tosClient, never()).putFile(anyString(), any(), anyString());
        assertTrue(!service.isIngestInFlight(RECORD_ID));
    }

    @Test
    void ingestFromUrl_zipNoMp4_marksFailedStatus5() throws Exception {
        ActivityRecordEntity row = existingRow(ActivityRecordEntity.STATUS_DOWNLOADING);
        when(activityRecordRepository.findByRecordId(RECORD_ID)).thenReturn(List.of(row));
        when(activityRecordRepository.save(any(ActivityRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(fileFetcher.fetchMp4(eq(DOWNLOAD_URL), any(Path.class)))
                .thenThrow(new java.io.IOException("zip 中无 .mp4"));

        service.ingestFromUrl(RECORD_ID, DOWNLOAD_URL);

        assertEquals(ActivityRecordEntity.STATUS_FAILED, row.getStatus());
        assertEquals(5, row.getStatus());
        assertNull(row.getTosUrl());
        verify(tosClient, never()).putFile(anyString(), any(), anyString());
    }

    @Test
    void ingestFromUrl_existing_tosPutFails_marksFailed() throws Exception {
        ActivityRecordEntity row = existingRow(ActivityRecordEntity.STATUS_DOWNLOADING);
        when(activityRecordRepository.findByRecordId(RECORD_ID)).thenReturn(List.of(row));
        when(activityRecordRepository.save(any(ActivityRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Path mp4 = tempDir.resolve("video.mp4");
        Files.write(mp4, new byte[] {0, 0, 0, 0, 'f', 't', 'y', 'p'});
        when(fileFetcher.fetchMp4(eq(DOWNLOAD_URL), any(Path.class))).thenReturn(mp4);
        String objectKey = ActivityRecordTosIngestService.buildObjectKey("vhall", ACTIVITY_ID, RECORD_ID);
        when(tosClient.putFile(eq(objectKey), eq(mp4), eq("video/mp4")))
                .thenThrow(new java.io.IOException("回放上传 TOS 失败: Access Denied"));

        service.ingestFromUrl(RECORD_ID, DOWNLOAD_URL);

        assertEquals(ActivityRecordEntity.STATUS_FAILED, row.getStatus());
        assertNull(row.getTosUrl());
        verify(activityRecordRepository).save(row);
        assertTrue(!service.isIngestInFlight(RECORD_ID));
    }

    @Test
    void processDownloadAsync_uncaughtFailure_marksFailedAndClearsInFlight() throws Exception {
        ActivityRecordEntity row = existingRow(ActivityRecordEntity.STATUS_DOWNLOADING);
        when(activityRecordRepository.findByRecordId(RECORD_ID)).thenReturn(List.of(row));
        when(activityRecordRepository.save(any(ActivityRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(fileFetcher.fetchMp4(eq(DOWNLOAD_URL), any(Path.class)))
                .thenThrow(new RuntimeException("disk full"));

        assertTrue(service.tryMarkInFlight(RECORD_ID));
        service.processDownloadAsync(RECORD_ID, DOWNLOAD_URL);

        assertEquals(ActivityRecordEntity.STATUS_FAILED, row.getStatus());
        assertTrue(!service.isIngestInFlight(RECORD_ID));
    }

    @Test
    void ingestFromUrl_existingReady_skips() throws Exception {
        ActivityRecordEntity row = existingRow(ActivityRecordEntity.STATUS_READY);
        row.setTosUrl(TOS_URL);
        when(activityRecordRepository.findByRecordId(RECORD_ID)).thenReturn(List.of(row));

        service.ingestFromUrl(RECORD_ID, DOWNLOAD_URL);

        verify(fileFetcher, never()).fetchMp4(anyString(), any());
        verify(tosClient, never()).putFile(anyString(), any(), anyString());
        verify(activityRecordRepository, never()).save(any());
    }

    @Test
    void ingestFromUrl_existingDownloading_stillFetches() throws Exception {
        // 调用方可能已预标 downloading；不能因此跳过真实下载
        ActivityRecordEntity row = existingRow(ActivityRecordEntity.STATUS_DOWNLOADING);
        when(activityRecordRepository.findByRecordId(RECORD_ID)).thenReturn(List.of(row));
        when(activityRecordRepository.save(any(ActivityRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Path mp4 = tempDir.resolve("video.mp4");
        Files.write(mp4, new byte[] {0, 0, 0, 0, 'f', 't', 'y', 'p'});
        when(fileFetcher.fetchMp4(eq(DOWNLOAD_URL), any(Path.class))).thenReturn(mp4);
        String objectKey = ActivityRecordTosIngestService.buildObjectKey("vhall", ACTIVITY_ID, RECORD_ID);
        when(tosClient.putFile(eq(objectKey), eq(mp4), eq("video/mp4"))).thenReturn(TOS_URL);

        service.ingestFromUrl(RECORD_ID, DOWNLOAD_URL);

        assertEquals(ActivityRecordEntity.STATUS_READY, row.getStatus());
        assertEquals(TOS_URL, row.getTosUrl());
        verify(fileFetcher).fetchMp4(eq(DOWNLOAD_URL), any(Path.class));
        verify(tosClient).putFile(objectKey, mp4, "video/mp4");
    }

    @Test
    void ingestFromUrl_blankUrl_marksFailed() throws Exception {
        ActivityRecordEntity row = existingRow(ActivityRecordEntity.STATUS_PACKAGING);
        when(activityRecordRepository.findByRecordId(RECORD_ID)).thenReturn(List.of(row));
        when(activityRecordRepository.save(any(ActivityRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.ingestFromUrl(RECORD_ID, "  ");

        assertEquals(ActivityRecordEntity.STATUS_FAILED, row.getStatus());
        verify(fileFetcher, never()).fetchMp4(anyString(), any());
    }

    @Test
    void ingestFromUrl_concurrentSameRecord_secondSkips() throws Exception {
        ActivityRecordEntity row = existingRow(ActivityRecordEntity.STATUS_PACKAGING);
        when(activityRecordRepository.findByRecordId(RECORD_ID)).thenReturn(List.of(row));
        when(activityRecordRepository.save(any(ActivityRecordEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        Path mp4 = tempDir.resolve("video.mp4");
        Files.write(mp4, new byte[] {1});
        when(fileFetcher.fetchMp4(eq(DOWNLOAD_URL), any(Path.class))).thenAnswer(inv -> {
            // 模拟首个任务仍在飞时第二次进入
            assertTrue(service.isIngestInFlight(RECORD_ID));
            service.ingestFromUrl(RECORD_ID, DOWNLOAD_URL);
            return mp4;
        });
        String objectKey = ActivityRecordTosIngestService.buildObjectKey("vhall", ACTIVITY_ID, RECORD_ID);
        when(tosClient.putFile(eq(objectKey), eq(mp4), eq("video/mp4"))).thenReturn(TOS_URL);

        service.ingestFromUrl(RECORD_ID, DOWNLOAD_URL);

        verify(fileFetcher, org.mockito.Mockito.times(1)).fetchMp4(eq(DOWNLOAD_URL), any(Path.class));
        assertEquals(ActivityRecordEntity.STATUS_READY, row.getStatus());
    }

    @Test
    void ingestFromUrl_missingRow_insertsDownloadingThenReady() throws Exception {
        when(activityRecordRepository.findByRecordId(RECORD_ID)).thenReturn(List.of());
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.getRecordInfo(RECORD_ID)).thenReturn(new ProviderRecordInfo(
                RECORD_ID,
                Long.parseLong(WEBINAR_ID),
                "回放A",
                100,
                "https://cover.example/a.png",
                2048L,
                "2026-09-01 10:00:00"));
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID);
        activity.setProvider("vhall");
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
        when(activityRecordRepository.findByActivityIdAndProviderAndRecordId(
                ACTIVITY_ID, "vhall", RECORD_ID)).thenReturn(Optional.empty());

        Path mp4 = tempDir.resolve("video.mp4");
        Files.write(mp4, new byte[] {1, 2, 3, 4});
        when(fileFetcher.fetchMp4(eq(DOWNLOAD_URL), any(Path.class))).thenReturn(mp4);
        String objectKey = ActivityRecordTosIngestService.buildObjectKey("vhall", ACTIVITY_ID, RECORD_ID);
        when(tosClient.putFile(eq(objectKey), eq(mp4), eq("video/mp4"))).thenReturn(TOS_URL);
        java.util.ArrayList<Integer> savedStatuses = new java.util.ArrayList<>();
        when(activityRecordRepository.save(any(ActivityRecordEntity.class)))
                .thenAnswer(inv -> {
                    ActivityRecordEntity e = inv.getArgument(0);
                    savedStatuses.add(e.getStatus());
                    return e;
                });

        service.ingestFromUrl(RECORD_ID, DOWNLOAD_URL);

        assertEquals(List.of(
                ActivityRecordEntity.STATUS_DOWNLOADING,
                ActivityRecordEntity.STATUS_READY), savedStatuses);
        ArgumentCaptor<ActivityRecordEntity> cap = ArgumentCaptor.forClass(ActivityRecordEntity.class);
        verify(activityRecordRepository, org.mockito.Mockito.times(2)).save(cap.capture());
        ActivityRecordEntity saved = cap.getAllValues().get(1);
        assertEquals(ACTIVITY_ID, saved.getActivityId());
        assertEquals(RECORD_ID, saved.getRecordId());
        assertEquals(objectKey, saved.getObjectKey());
        assertEquals(TOS_URL, saved.getTosUrl());
        assertEquals("回放A", saved.getTitle());
    }

    @Test
    void ingestFromUrl_missingRow_fetchFails_marksFailed() throws Exception {
        when(activityRecordRepository.findByRecordId(RECORD_ID)).thenReturn(List.of());
        when(providerRegistry.require("vhall")).thenReturn(liveProvider);
        when(liveProvider.getRecordInfo(RECORD_ID)).thenReturn(new ProviderRecordInfo(
                RECORD_ID, Long.parseLong(WEBINAR_ID), "回放B", 1, null, 1L, null));
        when(roomIdResolver.resolveRoomId(WEBINAR_ID)).thenReturn(Optional.of(ACTIVITY_ID));
        ActivityEntity activity = new ActivityEntity();
        activity.setId(ACTIVITY_ID);
        activity.setProvider("vhall");
        when(activityRepository.findById(ACTIVITY_ID)).thenReturn(Optional.of(activity));
        when(activityRecordRepository.findByActivityIdAndProviderAndRecordId(
                ACTIVITY_ID, "vhall", RECORD_ID)).thenReturn(Optional.empty());
        java.util.ArrayList<Integer> savedStatuses = new java.util.ArrayList<>();
        when(activityRecordRepository.save(any(ActivityRecordEntity.class)))
                .thenAnswer(inv -> {
                    ActivityRecordEntity e = inv.getArgument(0);
                    savedStatuses.add(e.getStatus());
                    return e;
                });
        when(fileFetcher.fetchMp4(eq(DOWNLOAD_URL), any(Path.class)))
                .thenThrow(new java.io.IOException("no mp4"));

        service.ingestFromUrl(RECORD_ID, DOWNLOAD_URL);

        assertEquals(List.of(
                ActivityRecordEntity.STATUS_DOWNLOADING,
                ActivityRecordEntity.STATUS_FAILED), savedStatuses);
        verify(tosClient, never()).putFile(anyString(), any(), anyString());
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
}
