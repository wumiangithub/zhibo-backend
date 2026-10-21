package com.zhibo.live.callback;

import com.zhibo.activity.RecordDemandFinalizeService;
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialRepository;
import com.zhibo.vhall.config.VhallProperties;
import com.zhibo.vhall.util.VhallCallbackSignUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * 微吼转码回调：进度 {@code method_id=38}、完成 {@code method_id=3}。
 * 按 {@code video_id}/{@code record_id} 对 {@code material.vhall_video_id} 更新进度与结果。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VhallTranscodeCallbackService {

    /** 转码完成（成功或失败） */
    static final int METHOD_COMPLETE = 3;
    /** 转码进度 */
    static final int METHOD_PROGRESS = 38;

    /** 本站 / 微吼完成回调：成功 */
    static final int STATUS_OK = 1;
    /** 进度回调：转码中（本站落库为 0） */
    static final int STATUS_PROGRESS_RUNNING = 2;
    /** 本站 / 微吼：失败 */
    static final int STATUS_FAIL = 3;
    /** 本站：已上传转码中 */
    static final int STATUS_PENDING = 0;

    private final VhallProperties vhallProperties;
    private final MaterialRepository materialRepository;
    private final RecordDemandFinalizeService recordDemandFinalizeService;

    public enum HandleResult {
        OK,
        BAD_SIGNATURE,
        BAD_REQUEST,
        IGNORED
    }

    @Transactional
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
            log.warn("微吼转码回调校签失败");
            return HandleResult.BAD_SIGNATURE;
        }

        Integer methodId = intVal(body.get("method_id"));
        if (methodId == null) {
            return HandleResult.BAD_REQUEST;
        }
        if (methodId != METHOD_PROGRESS && methodId != METHOD_COMPLETE) {
            log.info("微吼转码回调 method_id={} 非进度/完成，忽略", methodId);
            return HandleResult.IGNORED;
        }

        String videoKey = methodId == METHOD_PROGRESS
                ? stringVal(body.get("video_id"))
                : stringVal(body.get("record_id"));
        if (videoKey == null) {
            // 完成回调偶发也带 video_id
            videoKey = stringVal(body.get("video_id"));
        }
        if (videoKey == null) {
            return HandleResult.BAD_REQUEST;
        }

        Optional<MaterialEntity> found =
                materialRepository.findByVhallVideoIdAndDeletedTimeIsNull(videoKey);
        if (found.isEmpty()) {
            log.warn("微吼转码回调找不到素材 vhall_video_id={}", videoKey);
            // 未知仍 success，避免微吼无限重试
            return HandleResult.OK;
        }

        MaterialEntity entity = found.get();
        Integer beforeStatus = entity.getVhallTranscodeStatus();
        if (methodId == METHOD_PROGRESS) {
            applyProgress(entity, body);
        } else {
            applyComplete(entity, body);
        }
        materialRepository.save(entity);
        notifyPendingActivities(entity, beforeStatus);
        return HandleResult.OK;
    }

    /** 素材转码终态变化时，收尾挂靠的 ready=0 录播活动。 */
    private void notifyPendingActivities(MaterialEntity entity, Integer beforeStatus) {
        Integer status = entity.getVhallTranscodeStatus();
        if (status == null || status.equals(beforeStatus)) {
            return;
        }
        if (status == STATUS_OK) {
            recordDemandFinalizeService.onMaterialTranscodeOk(entity.getId());
        } else if (status == STATUS_FAIL) {
            recordDemandFinalizeService.onMaterialTranscodeFail(entity.getId());
        }
    }

    private void applyProgress(MaterialEntity entity, Map<String, Object> body) {
        Integer progress = parseProgress(body.get("progress"));
        if (progress != null) {
            entity.setVhallTranscodeProgress(progress);
        }
        Integer status = intVal(body.get("status"));
        if (status == null) {
            if (entity.getVhallTranscodeStatus() == null) {
                entity.setVhallTranscodeStatus(STATUS_PENDING);
            }
            return;
        }
        if (status == STATUS_OK) {
            entity.setVhallTranscodeStatus(STATUS_OK);
            if (progress == null || progress < 100) {
                entity.setVhallTranscodeProgress(100);
            }
        } else if (status == STATUS_FAIL) {
            entity.setVhallTranscodeStatus(STATUS_FAIL);
        } else {
            // 2 转码中，或其他 → 本站 0
            entity.setVhallTranscodeStatus(STATUS_PENDING);
        }
    }

    private void applyComplete(MaterialEntity entity, Map<String, Object> body) {
        Integer status = intVal(body.get("status"));
        if (status == null || (status != STATUS_OK && status != STATUS_FAIL)) {
            log.warn("微吼转码完成回调 status 非法: {}", status);
            return;
        }
        entity.setVhallTranscodeStatus(status);
        if (status == STATUS_OK) {
            entity.setVhallTranscodeProgress(100);
        }
    }

    /** {@code "60%"} / {@code 60} / {@code "60"} → 0～100；解析失败返回 null。 */
    static Integer parseProgress(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return clampProgress(number.intValue());
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.endsWith("%")) {
            text = text.substring(0, text.length() - 1).trim();
        }
        try {
            return clampProgress(Integer.parseInt(text));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static int clampProgress(int value) {
        if (value < 0) {
            return 0;
        }
        if (value > 100) {
            return 100;
        }
        return value;
    }

    private static String stringVal(Object raw) {
        if (raw == null) {
            return null;
        }
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }

    private static Integer intVal(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(raw).trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
