-- D85：流水表改名并加 socket_id；回放观看表；活动实际开播/结束时间

RENAME TABLE presence_event TO room_presence_event;

ALTER TABLE room_presence_event
  ADD COLUMN socket_id VARCHAR(64) NULL COMMENT '微吼长连接 id。新回调必有；历史行空，不参与配对' AFTER user_id,
  ADD KEY idx_presence_room_user_socket (room_id, user_id, socket_id),
  DROP INDEX uk_presence_client_dedupe,
  ADD UNIQUE KEY uk_presence_client_dedupe (room_id, user_id, type, event_time, source, socket_id),
  COMMENT = '进出流水。每次 Join/Leave 一行。user_id 为邮箱里的用户 id；socket_id 配对同一次连接';

ALTER TABLE activity
  ADD COLUMN live_actual_start_time DATETIME(3) NULL COMMENT '开播回调 type=1 的 time，首次写入不覆盖' AFTER end_time,
  ADD COLUMN live_actual_end_time DATETIME(3) NULL COMMENT '结束回调 type=3 的 time，首次写入不覆盖' AFTER live_actual_start_time;

CREATE TABLE room_replay_watch (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  room_id VARCHAR(32) NOT NULL COMMENT '本站活动 UUID',
  viewer_id BIGINT NOT NULL COMMENT 'viewer.id',
  record_id BIGINT NOT NULL COMMENT '微吼回放 id',
  socket_id VARCHAR(32) NOT NULL COMMENT '本站点播放时生成的会话 id',
  start_time DATETIME(3) NOT NULL COMMENT '点击播放时的服务器时间',
  last_report_time DATETIME(3) NOT NULL COMMENT '最近一次成功上报',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_replay_socket (socket_id),
  KEY idx_replay_room_viewer (room_id, viewer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='回放观看。一次播放一行，无微吼回调';
