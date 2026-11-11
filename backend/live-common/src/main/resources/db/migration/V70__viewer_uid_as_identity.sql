-- D117：业务用户 id 全面切到用户中心 uid；清空旧观众数据；viewer_id 列改名为 uid

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

-- 2) 去掉指向 viewer(id) 的外键
ALTER TABLE course_lesson_progress DROP FOREIGN KEY fk_clp_viewer;
ALTER TABLE course_user DROP FOREIGN KEY fk_course_user_viewer;
ALTER TABLE activity_audience DROP FOREIGN KEY fk_activity_audience_viewer;
ALTER TABLE viewer_feedback DROP FOREIGN KEY fk_viewer_feedback_viewer;
ALTER TABLE viewer_tag_member DROP FOREIGN KEY fk_vtm_viewer;

-- 3) viewer_id → uid
ALTER TABLE course_lesson_progress
  CHANGE COLUMN viewer_id uid BIGINT NOT NULL COMMENT '用户中心 uid（viewer.uid）';
ALTER TABLE course_user
  CHANGE COLUMN viewer_id uid BIGINT NOT NULL COMMENT '用户中心 uid（viewer.uid）';
ALTER TABLE activity_audience
  CHANGE COLUMN viewer_id uid BIGINT NOT NULL COMMENT '用户中心 uid（viewer.uid）';
ALTER TABLE viewer_feedback
  CHANGE COLUMN viewer_id uid BIGINT NOT NULL COMMENT '用户中心 uid（viewer.uid）';
ALTER TABLE viewer_tag_member
  CHANGE COLUMN viewer_id uid BIGINT NOT NULL COMMENT '用户中心 uid（viewer.uid）';
ALTER TABLE room_replay_watch
  CHANGE COLUMN viewer_id uid BIGINT NOT NULL COMMENT '用户中心 uid（viewer.uid）';
ALTER TABLE room_comment
  CHANGE COLUMN viewer_id uid BIGINT NULL COMMENT '用户中心 uid（viewer.uid）；中控台发券等可空';

-- 4) 索引名（可选对齐；部分索引随列改名仍可用，显式重命名便于对照）
ALTER TABLE course_lesson_progress
  RENAME INDEX uk_course_lesson_viewer TO uk_course_lesson_uid,
  RENAME INDEX idx_clp_course_viewer TO idx_clp_course_uid;
ALTER TABLE course_user
  RENAME INDEX uk_course_user TO uk_course_user_uid,
  RENAME INDEX idx_course_user_viewer TO idx_course_user_uid;
ALTER TABLE activity_audience
  RENAME INDEX uk_activity_audience TO uk_activity_audience_uid,
  RENAME INDEX idx_activity_audience_viewer TO idx_activity_audience_uid;
ALTER TABLE viewer_feedback
  RENAME INDEX idx_viewer_feedback_viewer TO idx_viewer_feedback_uid;
ALTER TABLE viewer_tag_member
  RENAME INDEX uk_viewer_tag_member TO uk_viewer_tag_member_uid,
  RENAME INDEX idx_viewer_tag_member_viewer TO idx_viewer_tag_member_uid;
ALTER TABLE room_replay_watch
  RENAME INDEX idx_replay_room_viewer TO idx_replay_room_uid;
ALTER TABLE room_comment
  RENAME INDEX idx_room_comment_viewer TO idx_room_comment_uid;

-- 5) 外键改挂 viewer(uid)；viewer.uid 已有 uk_viewer_uid
ALTER TABLE course_lesson_progress
  ADD CONSTRAINT fk_clp_viewer_uid FOREIGN KEY (uid) REFERENCES viewer (uid);
ALTER TABLE course_user
  ADD CONSTRAINT fk_course_user_viewer_uid FOREIGN KEY (uid) REFERENCES viewer (uid);
ALTER TABLE activity_audience
  ADD CONSTRAINT fk_activity_audience_viewer_uid FOREIGN KEY (uid) REFERENCES viewer (uid);
ALTER TABLE viewer_feedback
  ADD CONSTRAINT fk_viewer_feedback_viewer_uid FOREIGN KEY (uid) REFERENCES viewer (uid);
ALTER TABLE viewer_tag_member
  ADD CONSTRAINT fk_vtm_viewer_uid FOREIGN KEY (uid) REFERENCES viewer (uid);
ALTER TABLE room_replay_watch
  ADD CONSTRAINT fk_replay_watch_viewer_uid FOREIGN KEY (uid) REFERENCES viewer (uid);
ALTER TABLE room_comment
  ADD CONSTRAINT fk_room_comment_viewer_uid FOREIGN KEY (uid) REFERENCES viewer (uid);

-- 6) viewer 表注释：id 仅自增；uid 为业务用户主键
ALTER TABLE viewer
  MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '仅库内自增主键，非业务用户 id',
  MODIFY COLUMN uid BIGINT NULL COMMENT '用户中心 uid；业务用户主键 / JWT sub / 订单中心 third_user_id',
  MODIFY COLUMN username VARCHAR(64) NULL COMMENT '同步自用户中心 username；订单中心 JWT username；本站展示昵称仍用 nickname';

SET FOREIGN_KEY_CHECKS = 1;
