package com.zhibo.vhall.provider;

import com.zhibo.liveprovider.dto.CreateRoomCommand;
import com.zhibo.liveprovider.dto.CreateRoomResult;
import com.zhibo.liveprovider.dto.ProviderRecordDownload;
import com.zhibo.liveprovider.dto.PushStreamInfo;
import com.zhibo.vhall.client.VhallClient;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.exception.VhallException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VhallLiveProviderTest {

    @Mock
    private VhallClient vhallClient;

    private VhallLiveProvider provider;

    @BeforeEach
    void setUp() {
        VhallProperties properties = new VhallProperties();
        properties.setAppKey("key");
        properties.setAppSecret("secret");
        provider = new VhallLiveProvider(
                vhallClient, properties, Clock.fixed(Instant.parse("2026-09-18T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void create_liveMapsToCreatePath() {
        when(vhallClient.postForData(eq(VhallLiveProvider.PATH_CREATE), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("webinar_id", 99L));

        CreateRoomResult result = provider.create(new CreateRoomCommand(
                "标题", "2026-10-01 20:00:00", 2, "简介", null, "https://cover", true, null));
        assertEquals("99", result.providerRoomId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).postForData(eq(VhallLiveProvider.PATH_CREATE), captor.capture(), eq(Map.class));
        assertEquals("标题", captor.getValue().get("subject"));
        assertEquals("2026-10-01 20:00", captor.getValue().get("start_time"));
        assertEquals(2, captor.getValue().get("webinar_type"));
        assertEquals("https://cover", captor.getValue().get("img_url"));
        assertEquals(1, captor.getValue().get("no_delay_webinar"));
    }

    @Test
    void create_demandRequiresRecordId() {
        assertThrows(IllegalArgumentException.class, () ->
                provider.create(new CreateRoomCommand(
                        "录播", "2026-10-01 20:00:00", 4, "简介", null, null, null, null)));
    }

    @Test
    void create_demandMapsPath() {
        when(vhallClient.postForData(eq(VhallLiveProvider.PATH_CREATE_DEMAND), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("webinar_id", "88"));

        CreateRoomResult result = provider.create(new CreateRoomCommand(
                "录播", "2026-10-01 20:00:00", 4, "简介", "799703", null, false, true));
        assertEquals("88", result.providerRoomId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).postForData(
                eq(VhallLiveProvider.PATH_CREATE_DEMAND), captor.capture(), eq(Map.class));
        assertEquals("799703", captor.getValue().get("record_id"));
        assertEquals(0, captor.getValue().get("webinar_curr_num"));
        assertEquals(0, captor.getValue().get("no_delay_webinar"));
        assertEquals(1, captor.getValue().get("is_timing"));
        assertEquals("2026-10-01 20:00", captor.getValue().get("start_time"));
    }

    @Test
    void create_demandOmitsStartTimeWhenNotTiming() {
        when(vhallClient.postForData(eq(VhallLiveProvider.PATH_CREATE_DEMAND), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("webinar_id", "88"));

        provider.create(new CreateRoomCommand(
                "录播", "2026-10-01 20:00:00", 4, "简介", "799703", null, false, false));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).postForData(
                eq(VhallLiveProvider.PATH_CREATE_DEMAND), captor.capture(), eq(Map.class));
        assertEquals(0, captor.getValue().get("is_timing"));
        assertNull(captor.getValue().get("start_time"));
    }

    @Test
    void createVideoByUrl_mapsUrlAndFilename() {
        when(vhallClient.postForData(eq(VhallLiveProvider.PATH_CREATE_BY_URL), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("video_id", 5768233));

        String videoId = provider.createVideoByUrl(
                "https://cdn.example/a.mp4", "产品介绍成片");
        assertEquals("5768233", videoId);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).postForData(
                eq(VhallLiveProvider.PATH_CREATE_BY_URL), captor.capture(), eq(Map.class));
        assertEquals("https://cdn.example/a.mp4", captor.getValue().get("url"));
        assertEquals("产品介绍成片", captor.getValue().get("filename"));
    }

    @Test
    void createVideoByUrl_requiresUrl() {
        assertThrows(IllegalArgumentException.class, () ->
                provider.createVideoByUrl("  ", "name"));
        verify(vhallClient, never()).postForData(
                eq(VhallLiveProvider.PATH_CREATE_BY_URL), anyMap(), eq(Map.class));
    }

    @Test
    void getVideoTranscodeStatus_mapsTranscodeStatus() {
        when(vhallClient.getForData(eq(VhallLiveProvider.PATH_VIDEO_INFO), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("id", 4732505, "transcode_status", 1));

        assertEquals(1, provider.getVideoTranscodeStatus("4732505"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).getForData(
                eq(VhallLiveProvider.PATH_VIDEO_INFO), captor.capture(), eq(Map.class));
        assertEquals("4732505", captor.getValue().get("video_id"));
    }

    @Test
    void create_omitsNoDelayWhenNull() {
        when(vhallClient.postForData(eq(VhallLiveProvider.PATH_CREATE), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("webinar_id", 1L));

        provider.create(new CreateRoomCommand(
                "标题", "2026-10-01 20:00:00", 1, null, null, null, null, null));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).postForData(eq(VhallLiveProvider.PATH_CREATE), captor.capture(), eq(Map.class));
        assertEquals(null, captor.getValue().get("no_delay_webinar"));
    }

    @Test
    void update_mapsCoverUrlToImgUrl() {
        when(vhallClient.postForData(eq(VhallLiveProvider.PATH_EDIT), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("webinar_id", 99L));

        provider.update("99", new com.zhibo.liveprovider.dto.UpdateRoomCommand(
                null, null, null, "https://cdn.example/cover.jpg"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).postForData(eq(VhallLiveProvider.PATH_EDIT), captor.capture(), eq(Map.class));
        assertEquals("99", captor.getValue().get("webinar_id"));
        assertEquals("https://cdn.example/cover.jpg", captor.getValue().get("img_url"));
    }

    @Test
    void listRecords_mapsVhallFields() {
        when(vhallClient.postForData(eq(VhallLiveProvider.PATH_RECORD_LIST), anyMap(), eq(Map.class)))
                .thenReturn(Map.of(
                        "total", 1,
                        "list", List.of(Map.of(
                                "id", 799703,
                                "name", "回放A",
                                "duration", "01:02:03",
                                "created_at", "2026-09-01 10:00:00",
                                "img_url", "https://cover",
                                "storage", "2048",
                                "save_time", "2027-09-01 10:00:00"
                        ))
                ));

        var result = provider.listRecords("666781572");
        assertEquals(1, result.total());
        assertEquals(1, result.list().size());
        assertEquals(799703L, result.list().get(0).id());
        assertEquals("回放A", result.list().get(0).title());
        assertEquals("01:02:03", result.list().get(0).duration());
        assertEquals("2026-09-01 10:00:00", result.list().get(0).createdTime());
        assertEquals("https://cover", result.list().get(0).coverUrl());
        assertEquals("2048", result.list().get(0).storage());
        assertEquals("2027-09-01 10:00:00", result.list().get(0).saveTime());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).postForData(eq(VhallLiveProvider.PATH_RECORD_LIST), captor.capture(), eq(Map.class));
        assertEquals("666781572", captor.getValue().get("webinar_id"));
        assertEquals(0, captor.getValue().get("pos"));
        assertEquals(100, captor.getValue().get("limit"));
        assertEquals(-1, captor.getValue().get("source"));
    }

    @Test
    void buildChatFilterUrl_defaultsEmailAndName() {
        when(vhallClient.postForData(eq(VhallLiveProvider.PATH_CHAT_FILTER), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("page_url", "https://e.vhall.com/v3/lives/authchat/99?live_token=t"));

        String pageUrl = provider.buildChatFilterUrl("99", null, null);
        assertEquals("https://e.vhall.com/v3/lives/authchat/99?live_token=t", pageUrl);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).postForData(eq(VhallLiveProvider.PATH_CHAT_FILTER), captor.capture(), eq(Map.class));
        assertEquals("99", captor.getValue().get("webinar_id"));
        assertEquals(VhallLiveProvider.DEFAULT_CHAT_FILTER_EMAIL, captor.getValue().get("email"));
        assertEquals(VhallLiveProvider.DEFAULT_CHAT_FILTER_NAME, captor.getValue().get("name"));
    }

    @Test
    void checkRecordDownload_mapsUrlStatusProgress() {
        when(vhallClient.getForData(eq(VhallLiveProvider.PATH_RECORD_CHECK_DOWNLOAD), anyMap(), eq(Map.class)))
                .thenReturn(Map.of(
                        "record_id", 5541984,
                        "download_url", "https://vod-download.vhall.com/a.zip",
                        "status", 1,
                        "progress", 100
                ));

        ProviderRecordDownload info = provider.checkRecordDownload(5541984L);
        assertEquals(5541984L, info.recordId());
        assertEquals("https://vod-download.vhall.com/a.zip", info.downloadUrl());
        assertEquals(1, info.status());
        assertEquals(100, info.progress());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).getForData(
                eq(VhallLiveProvider.PATH_RECORD_CHECK_DOWNLOAD), captor.capture(), eq(Map.class));
        assertEquals(5541984L, captor.getValue().get("record_id"));
        assertEquals(1, captor.getValue().size());
        verify(vhallClient, never()).postForData(
                eq(VhallLiveProvider.PATH_RECORD_DOWNLOAD), anyMap(), eq(Object.class));
    }

    @Test
    void checkRecordDownload_submitsTaskWhenNotStarted() {
        when(vhallClient.getForData(eq(VhallLiveProvider.PATH_RECORD_CHECK_DOWNLOAD), anyMap(), eq(Map.class)))
                .thenReturn(Map.of(
                        "record_id", 5814669,
                        "status", 0,
                        "progress", 0
                ));
        when(vhallClient.postForData(eq(VhallLiveProvider.PATH_RECORD_DOWNLOAD), anyMap(), eq(Object.class)))
                .thenReturn(List.of());

        ProviderRecordDownload info = provider.checkRecordDownload(5814669L);
        assertEquals(5814669L, info.recordId());
        assertNull(info.downloadUrl());
        assertEquals(0, info.status());
        assertEquals(0, info.progress());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).postForData(
                eq(VhallLiveProvider.PATH_RECORD_DOWNLOAD), captor.capture(), eq(Object.class));
        assertEquals(5814669L, captor.getValue().get("record_id"));
        assertEquals(VhallLiveProvider.RECORD_DOWNLOAD_QUALITY_SAME, captor.getValue().get("quality"));
        assertEquals(2, captor.getValue().size());
    }

    @Test
    void getPushStream_mapsAddressCodeAndExpire() {
        when(vhallClient.getForData(eq(VhallLiveProvider.PATH_STREAM_PUSH), anyMap(), eq(Map.class)))
                .thenReturn(Map.of(
                        "stream_code", "lss_ede878a9?vhost=thirdVhost&token=abc",
                        "stream_address", "rtmp://rslive.vhall.com:1935/vhallyun/",
                        "expire_time", "2026-02-15 00:00:01"
                ));

        PushStreamInfo info = provider.getPushStream("752172335");
        assertEquals("rtmp://rslive.vhall.com:1935/vhallyun/", info.streamAddress());
        assertEquals("lss_ede878a9?vhost=thirdVhost&token=abc", info.streamCode());
        assertEquals("2026-02-15 00:00:01", info.expireTime());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).getForData(eq(VhallLiveProvider.PATH_STREAM_PUSH), captor.capture(), eq(Map.class));
        assertEquals("752172335", captor.getValue().get("webinar_id"));
        assertEquals(1, captor.getValue().size());
    }

    @Test
    void start_callsVhallStartThenReadsState() {
        when(vhallClient.postForData(eq(VhallLiveProvider.PATH_START), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("switch_id", 4240761));
        when(vhallClient.postForData(eq(VhallLiveProvider.PATH_INFO), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("webinar_state", 1, "subject", "标题"));

        int state = provider.start("752172335");
        assertEquals(1, state);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).postForData(eq(VhallLiveProvider.PATH_START), captor.capture(), eq(Map.class));
        assertEquals("752172335", captor.getValue().get("webinar_id"));
        assertEquals(VhallLiveProvider.START_TYPE_THIRD_PARTY, captor.getValue().get("start_type"));
        assertEquals(2, captor.getValue().size());
        verify(vhallClient).postForData(eq(VhallLiveProvider.PATH_INFO), anyMap(), eq(Map.class));
    }

    @Test
    void start_propagatesVhallFailure() {
        VhallException failure = new VhallException(512002, "rid", "活动不存在");
        when(vhallClient.postForData(eq(VhallLiveProvider.PATH_START), anyMap(), eq(Map.class)))
                .thenThrow(failure);

        VhallException thrown = assertThrows(VhallException.class, () -> provider.start("99"));
        assertEquals(failure, thrown);
        verify(vhallClient, never()).postForData(eq(VhallLiveProvider.PATH_INFO), anyMap(), eq(Map.class));
    }

    @Test
    void getPushStream_propagatesVhallFailure() {
        VhallException failure = new VhallException(512002, "rid", "活动不存在");
        when(vhallClient.getForData(eq(VhallLiveProvider.PATH_STREAM_PUSH), anyMap(), eq(Map.class)))
                .thenThrow(failure);

        VhallException thrown = assertThrows(VhallException.class, () -> provider.getPushStream("99"));
        assertEquals(failure, thrown);
    }

    @Test
    void getStreamStatus_mapsZeroAndOne() {
        when(vhallClient.getForData(eq(VhallLiveProvider.PATH_STREAM_STATUS), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("status", 0));
        assertEquals(0, provider.getStreamStatus("752172335"));

        when(vhallClient.getForData(eq(VhallLiveProvider.PATH_STREAM_STATUS), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("status", 1));
        assertEquals(1, provider.getStreamStatus("752172335"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient, times(2)).getForData(
                eq(VhallLiveProvider.PATH_STREAM_STATUS), captor.capture(), eq(Map.class));
        assertEquals("752172335", captor.getAllValues().get(0).get("webinar_id"));
        assertEquals(1, captor.getAllValues().get(0).size());
    }

    @Test
    void getStreamStatus_rejectsMissingStatus() {
        when(vhallClient.getForData(eq(VhallLiveProvider.PATH_STREAM_STATUS), anyMap(), eq(Map.class)))
                .thenReturn(Map.of());
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> provider.getStreamStatus("752172335"));
        assertEquals("微吼未返回推流状态", ex.getMessage());
    }

    @Test
    void getStreamStatus_propagatesVhallFailure() {
        VhallException failure = new VhallException(512002, "rid", "活动不存在");
        when(vhallClient.getForData(eq(VhallLiveProvider.PATH_STREAM_STATUS), anyMap(), eq(Map.class)))
                .thenThrow(failure);

        VhallException thrown = assertThrows(VhallException.class, () -> provider.getStreamStatus("99"));
        assertEquals(failure, thrown);
    }

    @Test
    void buildChatFilterUrl_rejectsLongName() {
        // 31 字，超过微吼 name ≤30
        String tooLong = "一二三四五六七八九十一二三四五六七八九十一二三四五六七八九十一";
        assertThrows(IllegalArgumentException.class, () ->
                provider.buildChatFilterUrl("99", "a@b.c", tooLong));
    }

    @Test
    void buildRoleJoinUrl_hostPassesEmailNicknameAvatarWhenPresent() {
        when(vhallClient.getForData(eq(VhallLiveProvider.PATH_ROLE_URL), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("page_url", "https://host.example/page"));

        String page = provider.buildRoleJoinUrl(
                "100",
                1,
                "10001@qkzb.com",
                "本场主播",
                "https://cdn.example/a.png?x=1&y=2");
        assertEquals("https://host.example/page", page);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).getForData(eq(VhallLiveProvider.PATH_ROLE_URL), captor.capture(), eq(Map.class));
        Map<String, Object> params = captor.getValue();
        assertEquals("100", params.get("webinar_id"));
        assertEquals(1, params.get("type"));
        assertEquals("10001@qkzb.com", params.get("email"));
        assertEquals("本场主播", params.get("nickname"));
        // avatar 去 query，微吼侧只收干净 base URL
        assertEquals("https://cdn.example/a.png", params.get("avatar"));
    }

    @Test
    void buildRoleJoinUrl_stripsAvatarQueryKeepsHttpBase() {
        when(vhallClient.getForData(eq(VhallLiveProvider.PATH_ROLE_URL), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("page_url", "https://host.example/page"));

        provider.buildRoleJoinUrl(
                "100", 1, "10001@qkzb.com", "主播", "http://cdn.example/b.jpg?token=abc&sig=1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).getForData(eq(VhallLiveProvider.PATH_ROLE_URL), captor.capture(), eq(Map.class));
        assertEquals("http://cdn.example/b.jpg", captor.getValue().get("avatar"));
    }

    @Test
    void buildRoleJoinUrl_omitsAvatarWhenNotUrlAfterStrip() {
        when(vhallClient.getForData(eq(VhallLiveProvider.PATH_ROLE_URL), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("page_url", "https://host.example/page"));

        provider.buildRoleJoinUrl("100", 1, "10001@qkzb.com", "主播", "?x=1&y=2");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).getForData(eq(VhallLiveProvider.PATH_ROLE_URL), captor.capture(), eq(Map.class));
        assertNull(captor.getValue().get("avatar"));
    }

    @Test
    void buildRoleJoinUrl_hostOmitsBlankIdentity() {
        when(vhallClient.getForData(eq(VhallLiveProvider.PATH_ROLE_URL), anyMap(), eq(Map.class)))
                .thenReturn(Map.of("page_url", "https://host.example/page"));

        provider.buildRoleJoinUrl("100", 1, null, null, null);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(vhallClient).getForData(eq(VhallLiveProvider.PATH_ROLE_URL), captor.capture(), eq(Map.class));
        Map<String, Object> params = captor.getValue();
        assertEquals("100", params.get("webinar_id"));
        assertEquals(1, params.get("type"));
        assertNull(params.get("email"));
        assertNull(params.get("nickname"));
        assertNull(params.get("avatar"));
    }
}
