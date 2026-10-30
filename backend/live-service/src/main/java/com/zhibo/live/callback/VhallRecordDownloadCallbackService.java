package com.zhibo.live.callback;

import com.zhibo.activity.record.ActivityRecordTosIngestService;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.util.VhallCallbackSignUtil;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 微吼回放打包完成回调（{@code method_id=6}）：校签后立即返回，异步下载→TOS→{@code ready}/{@code failed}。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VhallRecordDownloadCallbackService {

    /** 回放下载（打包完成） */
    static final int METHOD_RECORD_DOWNLOAD = 6;
    /** 打包成功 */
    static final int STATUS_OK = 1;
    /** 打包失败 */
    static final int STATUS_FAIL = 3;

    private final VhallProperties vhallProperties;
    private final ActivityRecordTosIngestService ingestService;

    public enum HandleResult {
        OK,
        BAD_SIGNATURE,
        BAD_REQUEST,
        IGNORED
    }

    public HandleResult handle(Map<String, Object> body) {
        if (body == null || body.isEmpty()) {
            return HandleResult.BAD_REQUEST;
        }

        String signature = stringVal(body.get("signature"));
        if (signature == null) {
            signature = stringVal(body.get("sign"));
        }
        String secret = vhallProperties.resolveCallbackSecret();
        if (secret == null || secret.isBlank() || !VhallCallbackSignUtil.matches(secret, body, signature)) {
            log.warn("微吼回放下载回调校签失败");
            return HandleResult.BAD_SIGNATURE;
        }

        Integer methodId = intVal(body.get("method_id"));
        if (methodId == null) {
            return HandleResult.BAD_REQUEST;
        }
        if (methodId != METHOD_RECORD_DOWNLOAD) {
            log.info("微吼回放下载回调 method_id={} 非打包完成，忽略", methodId);
            return HandleResult.IGNORED;
        }

        Integer status = intVal(body.get("status"));
        if (status == null || (status != STATUS_OK && status != STATUS_FAIL)) {
            return HandleResult.BAD_REQUEST;
        }

        Long recordId = longVal(body.get("record_id"));
        if (recordId == null || recordId <= 0) {
            return HandleResult.BAD_REQUEST;
        }

        if (status == STATUS_FAIL) {
            ingestService.processFailAsync(recordId);
            log.info("微吼回放下载回调 status=3 已投递异步标 failed record_id={}", recordId);
            return HandleResult.OK;
        }

        String downloadUrl = stringVal(body.get("download_url"));
        if (downloadUrl == null) {
            log.warn("微吼回放下载回调 status=1 但无 download_url record_id={}", recordId);
            return HandleResult.OK;
        }

        if (!ingestService.tryMarkInFlight(recordId)) {
            log.info("微吼回放下载回调已有 ingest in-flight，跳过重复投递 record_id={}", recordId);
            return HandleResult.OK;
        }
        ingestService.processDownloadAsync(recordId, downloadUrl);
        log.info("微吼回放下载回调已投递异步落 TOS record_id={}", recordId);
        return HandleResult.OK;
    }

    private static String stringVal(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim();
        return s.isEmpty() || "null".equalsIgnoreCase(s) ? null : s;
    }

    private static Integer intVal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Long longVal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        String s = stringVal(raw);
        if (s == null) {
            return null;
        }
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
