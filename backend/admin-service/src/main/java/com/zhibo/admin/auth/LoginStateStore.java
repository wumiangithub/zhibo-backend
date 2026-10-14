package com.zhibo.admin.auth;

import com.zhibo.admin.auth.dto.AuthUserResponse;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** 微信扫码会话 state（内存 + TTL）。 */
@Component
public class LoginStateStore {

    public static final int DEFAULT_EXPIRES_IN = 180;

    public enum Status {
        PENDING,
        SUCCESS,
        FAILED,
        EXPIRED
    }

    public static final class State {
        private volatile Status status;
        private final long userId;
        private final Instant expiresAt;
        private volatile String errorMsg;
        private volatile String accessToken;
        private volatile Integer expiresIn;
        private volatile String refreshToken;
        private volatile Integer refreshExpiresIn;
        private volatile AuthUserResponse user;

        State(Status status, long userId, Instant expiresAt) {
            this.status = status;
            this.userId = userId;
            this.expiresAt = expiresAt;
        }

        public Status getStatus() {
            return status;
        }

        public long getUserId() {
            return userId;
        }

        public Instant getExpiresAt() {
            return expiresAt;
        }

        public String getErrorMsg() {
            return errorMsg;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public Integer getExpiresIn() {
            return expiresIn;
        }

        public String getRefreshToken() {
            return refreshToken;
        }

        public Integer getRefreshExpiresIn() {
            return refreshExpiresIn;
        }

        public AuthUserResponse getUser() {
            return user;
        }
    }

    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentHashMap<String, State> store = new ConcurrentHashMap<>();

    public LoginStateStore(Clock clock) {
        this.clock = clock;
    }

    public String createPending(long userId, int expiresInSeconds) {
        purge();
        String state = "NCRM_" + randomHex(32);
        Instant expiresAt = clock.instant().plusSeconds(expiresInSeconds);
        store.put(state, new State(Status.PENDING, userId, expiresAt));
        return state;
    }

    public State get(String state) {
        purge();
        if (state == null || state.isBlank()) {
            return null;
        }
        State s = store.get(state);
        if (s == null) {
            return null;
        }
        if (s.status == Status.PENDING && s.expiresAt.isBefore(clock.instant())) {
            s.status = Status.EXPIRED;
        }
        return s;
    }

    public void markSuccess(
            String state,
            String accessToken,
            int expiresIn,
            String refreshToken,
            int refreshExpiresIn,
            AuthUserResponse user) {
        State s = get(state);
        if (s == null || s.status != Status.PENDING) {
            return;
        }
        s.accessToken = accessToken;
        s.expiresIn = expiresIn;
        s.refreshToken = refreshToken;
        s.refreshExpiresIn = refreshExpiresIn;
        s.user = user;
        s.status = Status.SUCCESS;
    }

    public void markFailed(String state, String errorMsg) {
        State s = get(state);
        if (s == null) {
            return;
        }
        if (s.status == Status.PENDING || s.status == Status.EXPIRED) {
            s.errorMsg = errorMsg;
            s.status = Status.FAILED;
        }
    }

    /** 成功后一次性消费；返回成功态快照后移除。 */
    public State consumeSuccess(String state) {
        State s = get(state);
        if (s == null) {
            return null;
        }
        if (s.status == Status.SUCCESS) {
            store.remove(state);
        }
        return s;
    }

    public void invalidate(String state) {
        if (state != null) {
            store.remove(state);
        }
    }

    private void purge() {
        Instant now = clock.instant();
        Iterator<Map.Entry<String, State>> it = store.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, State> e = it.next();
            State s = e.getValue();
            if (s.status == Status.PENDING && s.expiresAt.isBefore(now)) {
                s.status = Status.EXPIRED;
            }
            // 终态多留一会儿供轮询，超过 10 分钟清掉
            if (s.status != Status.PENDING && s.expiresAt.plusSeconds(600).isBefore(now)) {
                it.remove();
            }
        }
    }

    private String randomHex(int bytes) {
        byte[] buf = new byte[bytes];
        random.nextBytes(buf);
        return HexFormat.of().formatHex(buf);
    }
}
