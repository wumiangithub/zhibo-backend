package com.zhibo.presence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface PresenceEventRepository extends JpaRepository<PresenceEventEntity, String> {

    boolean existsByMid(String mid);

    boolean existsByRoomIdAndUserIdAndTypeAndEventTimeAndSource(
            String roomId, String userId, String type, LocalDateTime eventTime, String source);

    boolean existsByRoomIdAndUserIdAndTypeAndEventTimeAndSourceAndSocketId(
            String roomId, String userId, String type, LocalDateTime eventTime, String source, String socketId);

    List<PresenceEventEntity> findByRoomIdAndEventTimeGreaterThanEqualAndEventTimeLessThanOrderByEventTimeAsc(
            String roomId, LocalDateTime startInclusive, LocalDateTime endExclusive);

    List<PresenceEventEntity> findByRoomId(String roomId);

    List<PresenceEventEntity> findByRoomIdAndUserIdOrderByEventTimeAscIdAsc(String roomId, String userId);
}
