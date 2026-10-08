package com.zhibo.activity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomAttendanceRepository extends JpaRepository<RoomAttendanceEntity, String> {

    Optional<RoomAttendanceEntity> findByRoomIdAndUserId(String roomId, String userId);

    List<RoomAttendanceEntity> findByRoomId(String roomId);
}
