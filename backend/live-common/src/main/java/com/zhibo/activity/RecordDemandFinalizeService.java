package com.zhibo.activity;

import com.zhibo.liveprovider.LiveProvider;
import com.zhibo.liveprovider.LiveProviderRegistry;
import com.zhibo.liveprovider.dto.CreateRoomCommand;
import com.zhibo.liveprovider.dto.CreateRoomResult;
import com.zhibo.liveprovider.dto.ProviderRoomSnapshot;
import com.zhibo.material.MaterialEntity;
import com.zhibo.material.MaterialRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 录播异步收尾：素材转码成功后对 {@code ready=0} 的活动调 create-demand。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecordDemandFinalizeService {

    private final ActivityRepository activityRepository;
    private final MaterialRepository materialRepository;
    private final LiveProviderRegistry providerRegistry;

    /**
     * 某素材转码成功后：挂靠该素材且仍 pending 的活动 → create-demand → ready=1。
     * create-demand 失败 → ready=2。
     */
    @Transactional
    public void onMaterialTranscodeOk(String materialId) {
        if (materialId == null || materialId.isBlank()) {
            return;
        }
        MaterialEntity material = materialRepository.findById(materialId.trim())
                .filter(m -> m.getDeletedTime() == null)
                .orElse(null);
        if (material == null) {
            return;
        }
        String videoId = material.getVhallVideoId();
        if (videoId == null || videoId.isBlank()) {
            log.warn("转码成功但素材无 vhall_video_id materialId={}", materialId);
            markFail(materialId.trim());
            return;
        }
        List<ActivityEntity> pending = activityRepository
                .findByRecordMaterialIdAndReadyAndDeletedTimeIsNull(
                        materialId.trim(), ActivityReady.PENDING);
        for (ActivityEntity activity : pending) {
            finalizeOne(activity, videoId.trim());
        }
    }

    /** 素材转码失败：挂靠活动标 ready=2。 */
    @Transactional
    public void onMaterialTranscodeFail(String materialId) {
        if (materialId == null || materialId.isBlank()) {
            return;
        }
        markFail(materialId.trim());
    }

    private void markFail(String materialId) {
        List<ActivityEntity> pending = activityRepository
                .findByRecordMaterialIdAndReadyAndDeletedTimeIsNull(
                        materialId, ActivityReady.PENDING);
        for (ActivityEntity activity : pending) {
            activity.setReady(ActivityReady.FAIL);
            activityRepository.save(activity);
            log.warn("录播活动转码失败 activityId={} materialId={}", activity.getId(), materialId);
        }
    }

    private void finalizeOne(ActivityEntity activity, String videoId) {
        try {
            LiveProvider liveProvider = providerRegistry.require(activity.getProvider());
            CreateRoomCommand cmd = new CreateRoomCommand(
                    activity.getTitle(),
                    activity.getStartTime(),
                    activity.getType(),
                    activity.getIntroduction(),
                    videoId,
                    activity.getCoverUrl(),
                    activity.getUltraLowLatency(),
                    activity.getIsTiming()
            );
            CreateRoomResult room = liveProvider.create(cmd);
            activity.setProviderRoomId(room.providerRoomId());
            activity.setReady(ActivityReady.OK);
            try {
                ProviderRoomSnapshot snap = liveProvider.fetchRoom(room.providerRoomId());
                if (snap.state() != null) {
                    activity.setState(snap.state());
                }
                if (snap.shareLink() != null) {
                    activity.setShareLink(snap.shareLink());
                }
                if (activity.getCoverUrl() == null && snap.coverUrl() != null) {
                    activity.setCoverUrl(snap.coverUrl());
                }
            } catch (RuntimeException ignored) {
                // 房间已建；拉详情失败不挡就绪
            }
            activityRepository.save(activity);
            log.info("录播 create-demand 成功 activityId={} webinarId={}",
                    activity.getId(), room.providerRoomId());
        } catch (RuntimeException ex) {
            activity.setReady(ActivityReady.FAIL);
            activityRepository.save(activity);
            log.error("录播 create-demand 失败 activityId={}: {}", activity.getId(), ex.getMessage());
        }
    }
}
