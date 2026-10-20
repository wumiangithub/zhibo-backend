-- D57：开播成员（主播/助理/嘉宾）一张表；归属人仍用 activity_owner
-- role：1 主播 / 2 助理 / 3 嘉宾（见 Java ActivityMemberRole）

CREATE TABLE activity_member (
  activity_id VARCHAR(36) NOT NULL COMMENT '本站活动 UUID',
  user_id BIGINT NOT NULL COMMENT '员工 sys_useraccount.rs_mainkey_id',
  role INT NOT NULL COMMENT '开播角色：1 主播 / 2 助理 / 3 嘉宾',
  sort_order INT NOT NULL COMMENT '同角色内展示顺序，从 0 起',
  nickname VARCHAR(64) NULL COMMENT '本场展示昵称；空则接口回员工姓名',
  avatar_url VARCHAR(512) NULL COMMENT '本场展示头像 URL；空则前端默认头像',
  tag VARCHAR(32) NULL COMMENT '直播间标签，如讲师、助教',
  PRIMARY KEY (activity_id, user_id),
  KEY idx_activity_member_role (activity_id, role),
  CONSTRAINT fk_activity_member_activity FOREIGN KEY (activity_id) REFERENCES activity (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='本场开播成员（主播/助理/嘉宾）；role 见 ActivityMemberRole；姓名实时查员工表；不同步微吼';

INSERT INTO activity_member (activity_id, user_id, role, sort_order, nickname, avatar_url, tag)
SELECT activity_id, user_id, 1, sort_order, NULL, NULL, NULL
FROM activity_host;

DROP TABLE activity_host;
