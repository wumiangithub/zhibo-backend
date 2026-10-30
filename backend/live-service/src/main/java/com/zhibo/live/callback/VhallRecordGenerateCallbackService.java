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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Map;
import java.util.Optional;

/**
 * 微吼回放生成回调（{@code method_id=4}）：成功则 get-info 落 {@code activity_record}
 *（{@code pending_package}）并 submit 打包成功后改为 {@code packaging}；已有行走 check-download 幂等。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VhallRecordGenerateCallbackService {

    /** 回放生成 */
    static final int METHOD_RECORD_GENERATE = 4;
    /** 生成成功 */
    static final int STATUS_OK = 1;
    /** 生成失败 */
    static final int STATUS_FAIL = 3;

    /** 微吼 check-download：未开始（Provider 内会 submit） */
    static final int PACK_NOT_STARTED = 0;
    /** 微吼 check-download：可下载 */
    static final int PACK_READY = 1;
    /** 微吼 check-download：打包中 */
    static final int PACK_IN_PROGRESS = 2;
    /** 微吼 check-download：打包失败，可再 submit */
    static final int PACK_FAILED = 3;

    private static final DateTimeFormatter CREATED_AT = DateTimeFormatter
            .ofPattern("uuuu-MM-dd HH:mm:ss")
            .withResolverStyle(ResolverStyle.STRICT);

    private final VhallProperties vhallProperties;
    private final VhallRoomIdResolver roomIdResolver;
    private final ActivityRepository activityRepository;
    private final ActivityRecordRepository activityRecordRepository;
    private final LiveProviderRegistry providerRegistry;

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
            log.warn("微吼回放生成回调校签失败");
            return HandleResult.BAD_SIGNATURE;
        }

        Integer methodId = intVal(body.get("method_id"));
        if (methodId == null) {
            return HandleResult.BAD_REQUEST;
        }
        if (methodId != METHOD_RECORD_GENERATE) {
            log.info("微吼回放生成回调 method_id={} 非生成回放，忽略", methodId);
            return HandleResult.IGNORED;
        }

        Integer status = intVal(body.get("status"));
        if (status == null || (status != STATUS_OK && status != STATUS_FAIL)) {
            return HandleResult.BAD_REQUEST;
        }
        if (status == STATUS_FAIL) {
            log.info("微吼回放生成回调 status=3 失败，不插行 record_id={}", body.get("record_id"));
            return HandleResult.OK;
        }

        String webinarId = stringVal(body.get("webinar_id"));
        Long recordId = longVal(body.get("record_id"));
        if (webinarId == null || recordId == null || recordId <= 0) {
            return HandleResult.BAD_REQUEST;
        }

        Optional<String> roomId = roomIdResolver.resolveRoomId(webinarId);
        if (roomId.isEmpty()) {
            log.warn("微吼回放生成回调找不到本站活动 webinar_id={} record_id={}", webinarId, recordId);
            return HandleResult.OK;
        }

        Optional<ActivityEntity> found = activityRepository.findById(roomId.get());
        if (found.isEmpty()) {
            log.warn("微吼回放生成回调活动已不存在 id={} webinar_id={}", roomId.get(), webinarId);
            return HandleResult.OK;
        }
        ActivityEntity activity = found.get();
        if (activity.getDeletedTime() != null) {
            log.info("微吼回放生成回调活动已软删 id={} webinar_id={}", activity.getId(), webinarId);
            return HandleResult.OK;
        }

        String provider = activity.getProvider();
        if (provider == null || provider.isBlank()) {
            log.warn("微吼回放生成回调活动无 provider id={}", activity.getId());
            return HandleResult.OK;
        }

        Optional<ActivityRecordEntity> existing = activityRecordRepository
                .findByActivityIdAndProviderAndRecordId(activity.getId(), provider, recordId);
        if (existing.isPresent()) {
            handleExisting(existing.get(), provider, recordId);
            return HandleResult.OK;
        }

        insertAndSubmit(activity, provider, recordId);
        return HandleResult.OK;
    }

    /**
     * 已有同行：ready 不覆盖 tos；否则 check-download，按 0/1/2/3 决定是否再 submit 并标 packaging。
     */
    private void handleExisting(ActivityRecordEntity row, String provider, long recordId) {
        if (Integer.valueOf(ActivityRecordEntity.STATUS_READY).equals(row.getStatus())) {
            log.info("微吼回放生成回调已 ready，跳过 submit activityId={} recordId={} tos={}",
                    row.getActivityId(), recordId, hasText(row.getTosUrl()));
            return;
        }
        LiveProvider liveProvider;
        try {
            liveProvider = providerRegistry.require(provider);
        } catch (RuntimeException ex) {
            log.warn("微吼回放生成回调取 LiveProvider 失败 provider={} recordId={}: {}",
                    provider, recordId, ex.getMessage());
            return;
        }
        try {
            ProviderRecordDownload pack = liveProvider.checkRecordDownload(recordId);
            Integer packStatus = pack == null ? null : pack.status();
            if (packStatus != null && packStatus == PACK_NOT_STARTED) {
                // check 只查询；0 须显式 submit
                liveProvider.submitRecordDownload(recordId);
                markPackaging(row);
                log.info("微吼回放生成回调已有行 pack=0 显式 submit → packaging activityId={} recordId={}",
                        row.getActivityId(), recordId);
            } else if (packStatus != null && packStatus == PACK_FAILED) {
                liveProvider.submitRecordDownload(recordId);
                markPackaging(row);
                log.info("微吼回放生成回调已有行 pack=3 再 submit → packaging activityId={} recordId={}",
                        row.getActivityId(), recordId);
            } else if (packStatus != null
                    && (packStatus == PACK_READY || packStatus == PACK_IN_PROGRESS)) {
                // 1/2：已可下或进行中，标 packaging 等事件 6（事件 4 不在此异步 ingest）
                markPackaging(row);
                log.info("微吼回放生成回调已有行 check packStatus={} → packaging activityId={} recordId={}",
                        packStatus, row.getActivityId(), recordId);
            } else {
                log.info("微吼回放生成回调已有行 check packStatus={} activityId={} recordId={}",
                        packStatus, row.getActivityId(), recordId);
            }
        } catch (RuntimeException ex) {
            log.warn("微吼回放生成回调已有行 check/submit 失败 activityId={} recordId={}: {}",
                    row.getActivityId(), recordId, ex.getMessage());
        }
    }

    private void insertAndSubmit(ActivityEntity activity, String provider, long recordId) {
        LiveProvider liveProvider;
        try {
            liveProvider = providerRegistry.require(provider);
        } catch (RuntimeException ex) {
            log.warn("微吼回放生成回调取 LiveProvider 失败 provider={} recordId={}: {}",
                    provider, recordId, ex.getMessage());
            return;
        }

        ProviderRecordInfo info;
        try {
            info = liveProvider.getRecordInfo(recordId);
        } catch (RuntimeException ex) {
            log.warn("微吼回放生成回调 get-info 失败 activityId={} recordId={}: {}",
                    activity.getId(), recordId, ex.getMessage());
            return;
        }

        ActivityRecordEntity entity = new ActivityRecordEntity();
        entity.setActivityId(activity.getId());
        entity.setProvider(provider);
        entity.setRecordId(recordId);
        entity.setTitle(info == null ? null : info.title());
        entity.setDurationSec(info == null ? null : info.durationSec());
        entity.setCoverUrl(info == null ? null : info.coverUrl());
        entity.setStorageKb(info == null ? null : info.storageKb());
        entity.setStatus(ActivityRecordEntity.STATUS_PENDING_PACKAGE);
        entity.setProviderCreatedTime(parseCreatedAt(info == null ? null : info.createdTime()));

        try {
            activityRecordRepository.save(entity);
        } catch (DataIntegrityViolationException ex) {
            // 并发插入同一 UK：改走已有行幂等
            log.info("微吼回放生成回调并发插行，改走已有行路径 activityId={} recordId={}",
                    activity.getId(), recordId);
            activityRecordRepository
                    .findByActivityIdAndProviderAndRecordId(activity.getId(), provider, recordId)
                    .ifPresent(row -> handleExisting(row, provider, recordId));
            return;
        }

        try {
            liveProvider.submitRecordDownload(recordId);
            entity.setStatus(ActivityRecordEntity.STATUS_PACKAGING);
            activityRecordRepository.save(entity);
            log.info("微吼回放生成回调已插 pending_package 并 submit → packaging activityId={} recordId={}",
                    activity.getId(), recordId);
        } catch (RuntimeException ex) {
            // 优先留 pending_package + 日志，便于下载兜底
            log.warn("微吼回放生成回调已插 pending_package 但 submit 失败，留待兜底 activityId={} recordId={}: {}",
                    activity.getId(), recordId, ex.getMessage());
        }
    }

    private void markPackaging(ActivityRecordEntity row) {
        if (Integer.valueOf(ActivityRecordEntity.STATUS_PACKAGING).equals(row.getStatus())) {
            return;
        }
        if (Integer.valueOf(ActivityRecordEntity.STATUS_DOWNLOADING).equals(row.getStatus())
                || Integer.valueOf(ActivityRecordEntity.STATUS_READY).equals(row.getStatus())) {
            return;
        }
        row.setStatus(ActivityRecordEntity.STATUS_PACKAGING);
        activityRecordRepository.save(row);
    }

    private LocalDateTime parseCreatedAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(raw.trim(), CREATED_AT);
        } catch (DateTimeParseException ex) {
            log.warn("微吼回放生成回调 created_at 无法解析: {}", raw);
            return null;
        }
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
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
