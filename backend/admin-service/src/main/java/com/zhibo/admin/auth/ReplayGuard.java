package com.zhibo.admin.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** {@code request_id} 短时防重放（内存，TTL 默认 10 分钟）。 */
@Component
public class ReplayGuard {

    private static final long TTL_SECONDS = 600;

    private final Clock clock;
    private final ConcurrentHashMap<String, Instant> seen = new ConcurrentHashMap<>();

    public ReplayGuard(Clock clock) {
        this.clock = clock;
    }

    public void consumeOrReject(String requestId) {
        purge();
        Instant now = clock.instant();
        Instant expiry = now.plusSeconds(TTL_SECONDS);
        Instant prev = seen.putIfAbsent(requestId, expiry);
        if (prev != null && prev.isAfter(now)) {
            throw new IllegalArgumentException("账号或密码错误");
        }
        if (prev != null) {
            seen.put(requestId, expiry);
        }
    }

    private void purge() {
        Instant now = clock.instant();
        Iterator<Map.Entry<String, Instant>> it = seen.entrySet().iterator();
        while (it.hasNext()) {
            if (it.next().getValue().isBefore(now)) {
                it.remove();
            }
        }
    }
}
