package com.zhibo.activity;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * webinar_id → 本站活动 UUID。只查本站表，不打微吼。进程内缓存。
 */
@Component
@RequiredArgsConstructor
public class VhallRoomIdResolver {

    static final String PROVIDER_VHALL = "vhall";

    private final ActivityRepository activityRepository;
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public Optional<String> resolveRoomId(String webinarId) {
        if (webinarId == null || webinarId.isBlank()) {
            return Optional.empty();
        }
        String key = webinarId.trim();
        String cached = cache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        return activityRepository.findByProviderAndProviderRoomId(PROVIDER_VHALL, key)
                .map(ActivityEntity::getId)
                .map(id -> {
                    cache.put(key, id);
                    return id;
                });
    }
}
