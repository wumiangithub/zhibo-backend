package com.zhibo.replay;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomReplayWatchRepository extends JpaRepository<RoomReplayWatchEntity, String> {

    Optional<RoomReplayWatchEntity> findBySocketId(String socketId);

    List<RoomReplayWatchEntity> findByRoomId(String roomId);
}
