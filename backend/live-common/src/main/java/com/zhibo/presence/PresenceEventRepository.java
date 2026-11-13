package com.zhibo.presence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface PresenceEventRepository extends JpaRepository<PresenceEventEntity, String> {

    boolean existsByMid(String mid);

    boolean existsByRoomIdAndUsernameAndTypeAndEventTimeAndSource(
            String roomId, String username, String type, LocalDateTime eventTime, String source);

    boolean existsByRoomIdAndUsernameAndTypeAndEventTimeAndSourceAndSocketId(
            String roomId,
            String username,
            String type,
            LocalDateTime eventTime,
            String source,
            String socketId);

    List<PresenceEventEntity> findByRoomIdAndEventTimeGreaterThanEqualAndEventTimeLessThanOrderByEventTimeAsc(
            String roomId, LocalDateTime startInclusive, LocalDateTime endExclusive);

    List<PresenceEventEntity> findByRoomId(String roomId);

    List<PresenceEventEntity> findByRoomIdIn(Collection<String> roomIds);

    List<PresenceEventEntity> findByRoomIdAndUsernameOrderByEventTimeAscIdAsc(String roomId, String username);
}
