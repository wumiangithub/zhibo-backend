package com.zhibo.live.callback;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhibo.activity.record.ActivityRecordTosIngestService;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.util.VhallCallbackSignUtil;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class VhallRecordDownloadCallbackServiceTest {

    private static final String SECRET = "callback-secret";
    private static final long RECORD_ID = 799703L;
    private static final String DOWNLOAD_URL = "https://vod.example/a.zip";

    @Mock
    private VhallProperties vhallProperties;
    @Mock
    private ActivityRecordTosIngestService ingestService;

    private VhallRecordDownloadCallbackService service;

    @BeforeEach
    void setUp() {
        service = new VhallRecordDownloadCallbackService(vhallProperties, ingestService);
        when(vhallProperties.resolveCallbackSecret()).thenReturn(SECRET);
    }

    @Test
    void handle_statusOk_schedulesAsyncDownload() {
        when(ingestService.tryMarkInFlight(RECORD_ID)).thenReturn(true);
        Map<String, Object> body = downloadBody(VhallRecordDownloadCallbackService.STATUS_OK);
        body.put("download_url", DOWNLOAD_URL);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallRecordDownloadCallbackService.HandleResult.OK, service.handle(body));
        verify(ingestService).tryMarkInFlight(RECORD_ID);
        verify(ingestService).processDownloadAsync(RECORD_ID, DOWNLOAD_URL);
        verify(ingestService, never()).processFailAsync(anyLong());
    }

    @Test
    void handle_statusOk_alreadyInFlight_skipsAsync() {
        when(ingestService.tryMarkInFlight(RECORD_ID)).thenReturn(false);
        Map<String, Object> body = downloadBody(VhallRecordDownloadCallbackService.STATUS_OK);
        body.put("download_url", DOWNLOAD_URL);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallRecordDownloadCallbackService.HandleResult.OK, service.handle(body));
        verify(ingestService).tryMarkInFlight(RECORD_ID);
        verify(ingestService, never()).processDownloadAsync(anyLong(), anyString());
    }

    @Test
    void handle_statusFail_schedulesAsyncFailed() {
        Map<String, Object> body = downloadBody(VhallRecordDownloadCallbackService.STATUS_FAIL);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallRecordDownloadCallbackService.HandleResult.OK, service.handle(body));
        verify(ingestService).processFailAsync(RECORD_ID);
        verify(ingestService, never()).processDownloadAsync(anyLong(), anyString());
    }

    @Test
    void handle_statusOkMissingUrl_stillOkNoAsync() {
        Map<String, Object> body = downloadBody(VhallRecordDownloadCallbackService.STATUS_OK);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallRecordDownloadCallbackService.HandleResult.OK, service.handle(body));
        verify(ingestService, never()).processDownloadAsync(anyLong(), anyString());
        verify(ingestService, never()).processFailAsync(anyLong());
    }

    @Test
    void handle_badSignatureRejected() {
        Map<String, Object> body = downloadBody(VhallRecordDownloadCallbackService.STATUS_OK);
        body.put("download_url", DOWNLOAD_URL);
        body.put("signature", "deadbeef");

        assertEquals(
                VhallRecordDownloadCallbackService.HandleResult.BAD_SIGNATURE,
                service.handle(body));
        verify(ingestService, never()).processDownloadAsync(anyLong(), anyString());
        verify(ingestService, never()).processFailAsync(anyLong());
    }

    @Test
    void handle_wrongMethodIgnored() {
        Map<String, Object> body = downloadBody(VhallRecordDownloadCallbackService.STATUS_OK);
        body.put("method_id", 4);
        body.put("download_url", DOWNLOAD_URL);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallRecordDownloadCallbackService.HandleResult.IGNORED, service.handle(body));
        verify(ingestService, never()).processDownloadAsync(eq(RECORD_ID), anyString());
    }

    @Test
    void handle_missingRecordId_badRequest() {
        Map<String, Object> body = downloadBody(VhallRecordDownloadCallbackService.STATUS_OK);
        body.remove("record_id");
        body.put("download_url", DOWNLOAD_URL);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(
                VhallRecordDownloadCallbackService.HandleResult.BAD_REQUEST,
                service.handle(body));
        verify(ingestService, never()).processDownloadAsync(anyLong(), anyString());
    }

    private static Map<String, Object> downloadBody(int status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("method_id", VhallRecordDownloadCallbackService.METHOD_RECORD_DOWNLOAD);
        body.put("refer", "vhall");
        body.put("time", 1_505_095_994L);
        body.put("record_id", RECORD_ID);
        body.put("status", status);
        body.put("mid", "mid-6");
        return body;
    }
}
