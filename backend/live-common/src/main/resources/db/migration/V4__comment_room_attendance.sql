-- 仅本站库 zhibo.room_attendance 字段说明（不动其他库）
ALTER TABLE room_attendance
  COMMENT = '观众进出房记录。一次进入一行；同一房间、同一用户、同一进入时间只有一行。再次进入不回填更早那一行的离开时间';

ALTER TABLE room_attendance
  MODIFY COLUMN id VARCHAR(36) NOT NULL
    COMMENT '本站进出记录 UUID。进入接口返回这个 id',
  MODIFY COLUMN room_id VARCHAR(36) NOT NULL
    COMMENT '本站活动 UUID，对应 activity.id。不是微吼 webinar_id',
  MODIFY COLUMN user_id VARCHAR(128) NOT NULL
    COMMENT '微吼进出推送里的用户 id。与 guestId 对不上时以推送 id 为准',
  MODIFY COLUMN nickname VARCHAR(128) NULL
    COMMENT '进房时观看页当前昵称。可空',
  MODIFY COLUMN avatar_url VARCHAR(1024) NULL
    COMMENT '头像 URL。没有就不传，本迭代通常为空',
  MODIFY COLUMN enter_time DATETIME(3) NOT NULL
    COMMENT '微吼推送的进入时间，格式 yyyy-MM-dd HH:mm:ss。后端不改写。与 room_id、user_id 组成唯一键',
  MODIFY COLUMN leave_time DATETIME(3) NULL
    COMMENT '微吼推送的离开时间。空表示这一次还没上报离开；已有值时重复上报不覆盖。活动结束后不批量补写',
  MODIFY COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    COMMENT '入库时间。只用于同一进入时间时，取更晚的那条做次序兜底';
