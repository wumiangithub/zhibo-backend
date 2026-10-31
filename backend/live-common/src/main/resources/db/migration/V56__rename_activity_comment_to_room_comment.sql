-- 评论表改名：activity_comment → room_comment（与 room_* 进出/回放同前缀）

RENAME TABLE activity_comment TO room_comment;

ALTER TABLE room_comment
  RENAME INDEX uk_activity_comment_provider TO uk_room_comment_provider,
  RENAME INDEX idx_activity_comment_activity_time TO idx_room_comment_activity_time,
  RENAME INDEX idx_activity_comment_viewer TO idx_room_comment_viewer,
  DROP FOREIGN KEY fk_activity_comment_activity,
  ADD CONSTRAINT fk_room_comment_activity FOREIGN KEY (activity_id) REFERENCES activity (id),
  COMMENT = '本站直播评论流水；live-web 上报';
