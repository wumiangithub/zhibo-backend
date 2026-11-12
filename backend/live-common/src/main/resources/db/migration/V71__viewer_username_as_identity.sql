-- D118：业务用户 id 全面切到用户中心 username；清空旧观众数据；uid 列改名为 username

SET FOREIGN_KEY_CHECKS = 0;

-- 1) 清空引用观众身份的业务表 + viewer 本身
TRUNCATE TABLE course_lesson_progress;
TRUNCATE TABLE course_user;
TRUNCATE TABLE course_user_import;
TRUNCATE TABLE activity_audience;
TRUNCATE TABLE activity_audience_import;
TRUNCATE TABLE room_comment;
TRUNCATE TABLE viewer_feedback;
TRUNCATE TABLE viewer_tag_member;
TRUNCATE TABLE room_replay_watch;
TRUNCATE TABLE room_presence_event;
TRUNCATE TABLE room_attendance;
TRUNCATE TABLE viewer;

-- 2) 去掉指向 viewer(uid) 的外键（V70）
ALTER TABLE course_lesson_progress DROP FOREIGN KEY fk_clp_viewer_uid;
ALTER TABLE course_user DROP FOREIGN KEY fk_course_user_viewer_uid;
ALTER TABLE activity_audience DROP FOREIGN KEY fk_activity_audience_viewer_uid;
ALTER TABLE viewer_feedback DROP FOREIGN KEY fk_viewer_feedback_viewer_uid;
ALTER TABLE viewer_tag_member DROP FOREIGN KEY fk_vtm_viewer_uid;
ALTER TABLE room_replay_watch DROP FOREIGN KEY fk_replay_watch_viewer_uid;
ALTER TABLE room_comment DROP FOREIGN KEY fk_room_comment_viewer_uid;

-- 3) uid → username
ALTER TABLE course_lesson_progress
  CHANGE COLUMN uid username VARCHAR(16) NOT NULL COMMENT '用户中心 username（viewer.username）';
ALTER TABLE course_user
  CHANGE COLUMN uid username VARCHAR(16) NOT NULL COMMENT '用户中心 username（viewer.username）';
ALTER TABLE activity_audience
  CHANGE COLUMN uid username VARCHAR(16) NOT NULL COMMENT '用户中心 username（viewer.username）';
ALTER TABLE viewer_feedback
  CHANGE COLUMN uid username VARCHAR(16) NOT NULL COMMENT '用户中心 username（viewer.username）';
ALTER TABLE viewer_tag_member
  CHANGE COLUMN uid username VARCHAR(16) NOT NULL COMMENT '用户中心 username（viewer.username）';
ALTER TABLE room_replay_watch
  CHANGE COLUMN uid username VARCHAR(16) NOT NULL COMMENT '用户中心 username（viewer.username）';
ALTER TABLE room_comment
  CHANGE COLUMN uid username VARCHAR(16) NULL COMMENT '用户中心 username；中控台发券等可空';

-- 4) 索引名对齐
ALTER TABLE course_lesson_progress
  RENAME INDEX uk_course_lesson_uid TO uk_course_lesson_username,
  RENAME INDEX idx_clp_course_uid TO idx_clp_course_username;
ALTER TABLE course_user
  RENAME INDEX uk_course_user_uid TO uk_course_user_username,
  RENAME INDEX idx_course_user_uid TO idx_course_user_username;
ALTER TABLE activity_audience
  RENAME INDEX uk_activity_audience_uid TO uk_activity_audience_username,
  RENAME INDEX idx_activity_audience_uid TO idx_activity_audience_username;
ALTER TABLE viewer_feedback
  RENAME INDEX idx_viewer_feedback_uid TO idx_viewer_feedback_username;
ALTER TABLE viewer_tag_member
  RENAME INDEX uk_viewer_tag_member_uid TO uk_viewer_tag_member_username,
  RENAME INDEX idx_viewer_tag_member_uid TO idx_viewer_tag_member_username;
ALTER TABLE room_replay_watch
  RENAME INDEX idx_replay_room_uid TO idx_replay_room_username;
ALTER TABLE room_comment
  RENAME INDEX idx_room_comment_uid TO idx_room_comment_username;

-- 5) viewer：先建 username 唯一索引（外键引用必须先有索引）
ALTER TABLE viewer
  DROP INDEX uk_viewer_uid,
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '仅库内自增主键，非业务用户 id',
  MODIFY COLUMN uid BIGINT NULL COMMENT '同步自用户中心 LoginVo.uid；仅备查，非业务身份（D118）',
  MODIFY COLUMN username VARCHAR(16) NULL COMMENT '用户中心 username；业务用户主键 / JWT sub / 订单 third_user_id（D118）',
  ADD UNIQUE KEY uk_viewer_username (username);

-- 6) 外键改挂 viewer(username)
ALTER TABLE course_lesson_progress
  ADD CONSTRAINT fk_clp_viewer_username FOREIGN KEY (username) REFERENCES viewer (username);
ALTER TABLE course_user
  ADD CONSTRAINT fk_course_user_viewer_username FOREIGN KEY (username) REFERENCES viewer (username);
ALTER TABLE activity_audience
  ADD CONSTRAINT fk_activity_audience_viewer_username FOREIGN KEY (username) REFERENCES viewer (username);
ALTER TABLE viewer_feedback
  ADD CONSTRAINT fk_viewer_feedback_viewer_username FOREIGN KEY (username) REFERENCES viewer (username);
ALTER TABLE viewer_tag_member
  ADD CONSTRAINT fk_vtm_viewer_username FOREIGN KEY (username) REFERENCES viewer (username);
ALTER TABLE room_replay_watch
  ADD CONSTRAINT fk_replay_watch_viewer_username FOREIGN KEY (username) REFERENCES viewer (username);
ALTER TABLE room_comment
  ADD CONSTRAINT fk_room_comment_viewer_username FOREIGN KEY (username) REFERENCES viewer (username);

SET FOREIGN_KEY_CHECKS = 1;
