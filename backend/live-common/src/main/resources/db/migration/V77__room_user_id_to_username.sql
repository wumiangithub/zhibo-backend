-- 进出名单/流水：库列 user_id → username（邮箱本地部分；观众为用户中心 username，员工 e*/z*）
ALTER TABLE room_attendance
  CHANGE COLUMN user_id username VARCHAR(128) NOT NULL
    COMMENT '邮箱本地部分：观众 username / e{员工id} / z{员工id}',
  DROP INDEX uk_room_user,
  ADD UNIQUE KEY uk_room_username (room_id, username),
  COMMENT = '观众进出房。同一 room_id + username 只有一行。再次进入更新 enter_time 并清空 leave_time，不插新行';

ALTER TABLE room_presence_event
  CHANGE COLUMN user_id username VARCHAR(128) NOT NULL
    COMMENT '邮箱本地部分：观众 username / e{员工id} / z{员工id}',
  DROP INDEX idx_presence_room_user_socket,
  ADD KEY idx_presence_room_username_socket (room_id, username, socket_id),
  DROP INDEX uk_presence_client_dedupe,
  ADD UNIQUE KEY uk_presence_client_dedupe (room_id, username, type, event_time, source, socket_id),
  COMMENT = '进出流水。每次 Join/Leave 一行。username 为邮箱本地部分；socket_id 配对同一次连接';
