CREATE TABLE room_attendance (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  room_id VARCHAR(36) NOT NULL,
  user_id VARCHAR(128) NOT NULL,
  nickname VARCHAR(128) NULL,
  avatar_url VARCHAR(1024) NULL,
  enter_time DATETIME(3) NOT NULL,
  leave_time DATETIME(3) NULL,
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_room_user_enter (room_id, user_id, enter_time),
  KEY idx_room_enter (room_id, enter_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
