package com.zhibo.activity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomAttendanceRepository extends JpaRepository<RoomAttendanceEntity, String> {

    Optional<RoomAttendanceEntity> findByRoomIdAndUsername(String roomId, String username);

    List<RoomAttendanceEntity> findByRoomId(String roomId);
}
