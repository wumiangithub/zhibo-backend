-- D35：进出流水表（走势图）；活动表 (provider, provider_room_id) 唯一，供回调反查本站 UUID

CREATE TABLE presence_event (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  room_id VARCHAR(36) NOT NULL,
  user_id VARCHAR(128) NOT NULL,
  type VARCHAR(16) NOT NULL COMMENT 'Join / Leave，与微吼回调一致',
  event_time DATETIME(3) NOT NULL COMMENT '事件真实时间 Asia/Shanghai',
  mid VARCHAR(64) NULL COMMENT '微吼回调 mid；客户端可空',
  source VARCHAR(16) NOT NULL COMMENT 'callback / client',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_presence_mid (mid),
  UNIQUE KEY uk_presence_client_dedupe (room_id, user_id, type, event_time, source),
  KEY idx_presence_room_time (room_id, event_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='进出流水。每次 Join/Leave 一行，只服务走势图';

-- 同一服务商同一外部房间只对应一场本站活动（NULL provider_room_id 允许多行）
ALTER TABLE activity
  ADD UNIQUE KEY uk_activity_provider_room (provider, provider_room_id);
