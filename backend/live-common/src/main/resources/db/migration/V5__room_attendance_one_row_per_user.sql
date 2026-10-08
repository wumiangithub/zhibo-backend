-- 同一房间同一用户只保留一行（与微吼按 room+user 计在线一致）
-- 若已有重复，先丢掉进入时间更早的行，再改唯一键

DELETE a FROM room_attendance a
INNER JOIN room_attendance b
  ON a.room_id = b.room_id
 AND a.user_id = b.user_id
 AND (
   a.enter_time < b.enter_time
   OR (a.enter_time = b.enter_time AND a.created_at < b.created_at)
   OR (a.enter_time = b.enter_time AND a.created_at = b.created_at AND a.id < b.id)
 );

ALTER TABLE room_attendance
  DROP INDEX uk_room_user_enter,
  ADD UNIQUE KEY uk_room_user (room_id, user_id);

ALTER TABLE room_attendance
  COMMENT = '观众进出房。同一 room_id + user_id 只有一行。再次进入更新 enter_time 并清空 leave_time，不插新行';

ALTER TABLE room_attendance
  MODIFY COLUMN user_id VARCHAR(128) NOT NULL
    COMMENT '微吼进出推送里的用户 id。与 room_id 组成唯一键。与 guestId 对不上时以推送 id 为准',
  MODIFY COLUMN nickname VARCHAR(128) NULL
    COMMENT '观看页当前昵称。可空。再次进入时请求里带了才覆盖，不传则保留',
  MODIFY COLUMN avatar_url VARCHAR(1024) NULL
    COMMENT '头像 URL。没有就不传。再次进入时请求里带了才覆盖，不传则保留',
  MODIFY COLUMN enter_time DATETIME(3) NOT NULL
    COMMENT '当前这次进入的微吼时间，格式 yyyy-MM-dd HH:mm:ss。再次进入会覆盖，并清空 leave_time',
  MODIFY COLUMN leave_time DATETIME(3) NULL
    COMMENT '当前这次离开时间。再次进入时清空。空表示这次还没上报离开；已有值时重复上报不覆盖。活动结束后不批量补写',
  MODIFY COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    COMMENT '首次入库时间。再次进入不改';
