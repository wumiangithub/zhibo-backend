package com.zhibo.live.viewer;

import com.zhibo.live.viewer.dto.ViewerPresencePingResponse;
import com.zhibo.viewer.ViewerEntity;
import com.zhibo.viewer.ViewerRepository;
import com.zhibo.viewer.ViewerStatus;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 观众最近访问心跳（D79）。 */
@Service
public class ViewerPresenceService {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    /** 距上次写入不足该分钟数则不落库。 */
    static final int THROTTLE_MINUTES = 5;

    private final ViewerRepository viewerRepository;
    private final Clock clock;

    public ViewerPresenceService(ViewerRepository viewerRepository, Clock clock) {
        this.viewerRepository = viewerRepository;
        this.clock = clock;
    }

    @Transactional
    public ViewerPresencePingResponse ping(long viewerId, String scene) {
        validateScene(scene);
        ViewerEntity viewer = viewerRepository
                .findById(viewerId)
                .orElseThrow(() -> new ViewerAuthException(403, "账号已停用"));
        if (!ViewerStatus.isNormal(viewer.getStatus())) {
            throw new ViewerAuthException(403, "账号已停用");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime previous = viewer.getLastVisitTime();
        if (previous == null || ChronoUnit.MINUTES.between(previous, now) >= THROTTLE_MINUTES) {
            viewer.setLastVisitTime(now);
            viewer.setUpdatedTime(now);
            viewerRepository.save(viewer);
            return new ViewerPresencePingResponse(TIME_FMT.format(now));
        }
        return new ViewerPresencePingResponse(TIME_FMT.format(previous));
    }

    private static void validateScene(String scene) {
        if (scene == null || scene.isBlank()) {
            return;
        }
        String value = scene.trim();
        if (!"miniapp".equals(value) && !"live".equals(value)) {
            throw new ViewerAuthException(400, "scene 仅支持 miniapp 或 live");
        }
    }
}
