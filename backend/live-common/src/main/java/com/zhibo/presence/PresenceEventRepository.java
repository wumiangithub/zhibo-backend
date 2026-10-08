package com.zhibo.presence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PresenceEventRepository extends JpaRepository<PresenceEventEntity, String> {

    boolean existsByMid(String mid);

    boolean existsByRoomIdAndUserIdAndTypeAndEventTimeAndSource(
            String roomId, String userId, String type, LocalDateTime eventTime, String source);

    List<PresenceEventEntity> findByRoomIdAndEventTimeGreaterThanEqualAndEventTimeLessThanOrderByEventTimeAsc(
            String roomId, LocalDateTime startInclusive, LocalDateTime endExclusive);
}
