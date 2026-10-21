package com.zhibo.live.callback;

import com.zhibo.activity.RecordDemandFinalizeService;
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialRepository;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.util.VhallCallbackSignUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VhallTranscodeCallbackServiceTest {

    private static final String SECRET = "callback-secret";

    @Mock
    private VhallProperties vhallProperties;
    @Mock
    private MaterialRepository materialRepository;
    @Mock
    private RecordDemandFinalizeService recordDemandFinalizeService;

    private VhallTranscodeCallbackService service;

    @BeforeEach
    void setUp() {
        service = new VhallTranscodeCallbackService(
                vhallProperties, materialRepository, recordDemandFinalizeService);
        lenient().when(vhallProperties.resolveCallbackSecret()).thenReturn(SECRET);
    }

    @Test
    void handle_progressUpdatesPercentAndPending() {
        MaterialEntity m = video("vid-1");
        when(materialRepository.findByVhallVideoIdAndDeletedTimeIsNull("4624281"))
                .thenReturn(Optional.of(m));

        Map<String, Object> body = progressBody("60%", 2);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallTranscodeCallbackService.HandleResult.OK, service.handle(body));

        ArgumentCaptor<MaterialEntity> cap = ArgumentCaptor.forClass(MaterialEntity.class);
        verify(materialRepository).save(cap.capture());
        assertEquals(60, cap.getValue().getVhallTranscodeProgress());
        assertEquals(VhallTranscodeCallbackService.STATUS_PENDING, cap.getValue().getVhallTranscodeStatus());
    }

    @Test
    void handle_completeSuccessSetsStatusAnd100() {
        MaterialEntity m = video("vid-1");
        m.setVhallTranscodeProgress(80);
        m.setVhallTranscodeStatus(0);
        when(materialRepository.findByVhallVideoIdAndDeletedTimeIsNull("56345"))
                .thenReturn(Optional.of(m));

        Map<String, Object> body = completeBody(1);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallTranscodeCallbackService.HandleResult.OK, service.handle(body));

        assertEquals(VhallTranscodeCallbackService.STATUS_OK, m.getVhallTranscodeStatus());
        assertEquals(100, m.getVhallTranscodeProgress());
        verify(materialRepository).save(m);
        verify(recordDemandFinalizeService).onMaterialTranscodeOk("vid-1");
    }

    @Test
    void handle_completeFailSetsStatus3() {
        MaterialEntity m = video("vid-1");
        when(materialRepository.findByVhallVideoIdAndDeletedTimeIsNull("56345"))
                .thenReturn(Optional.of(m));

        Map<String, Object> body = completeBody(3);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallTranscodeCallbackService.HandleResult.OK, service.handle(body));
        assertEquals(VhallTranscodeCallbackService.STATUS_FAIL, m.getVhallTranscodeStatus());
        verify(recordDemandFinalizeService).onMaterialTranscodeFail("vid-1");
    }

    @Test
    void handle_badSignatureRejected() {
        Map<String, Object> body = progressBody("10%", 2);
        body.put("signature", "deadbeef");

        assertEquals(VhallTranscodeCallbackService.HandleResult.BAD_SIGNATURE, service.handle(body));
        verify(materialRepository, never()).save(any());
    }

    @Test
    void handle_unknownVideoStillOk() {
        when(materialRepository.findByVhallVideoIdAndDeletedTimeIsNull("4624281"))
                .thenReturn(Optional.empty());
        Map<String, Object> body = progressBody("10%", 2);
        body.put("signature", VhallCallbackSignUtil.sign(SECRET, body));

        assertEquals(VhallTranscodeCallbackService.HandleResult.OK, service.handle(body));
        verify(materialRepository, never()).save(any());
    }

    @Test
    void parseProgress_percentString() {
        assertEquals(60, VhallTranscodeCallbackService.parseProgress("60%"));
        assertEquals(100, VhallTranscodeCallbackService.parseProgress(100));
        assertNull(VhallTranscodeCallbackService.parseProgress("x"));
    }

    private static MaterialEntity video(String id) {
        MaterialEntity m = new MaterialEntity();
        m.setId(id);
        m.setType("video");
        m.setVhallVideoId("4624281");
        return m;
    }

    private static Map<String, Object> progressBody(String progress, int status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("method_id", VhallTranscodeCallbackService.METHOD_PROGRESS);
        body.put("refer", "vhall");
        body.put("video_id", 4624281);
        body.put("progress", progress);
        body.put("status", status);
        body.put("time", 1_732_593_217L);
        body.put("mid", "mid-progress-1");
        body.put("user_id", 72035607);
        body.put("child_user_id", 72035607);
        return body;
    }

    private static Map<String, Object> completeBody(int status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("method_id", VhallTranscodeCallbackService.METHOD_COMPLETE);
        body.put("refer", "vhall");
        body.put("record_id", 56345);
        body.put("status", status);
        body.put("time", 1_505_095_994L);
        body.put("mid", "mid-complete-1");
        body.put("user_id", 234567);
        body.put("child_user_id", 345678);
        return body;
    }
}
