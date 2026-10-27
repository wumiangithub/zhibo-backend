-- D77：直播用户运营名单 + 本站评论流水

CREATE TABLE activity_audience (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '行主键',
  activity_id VARCHAR(32) NOT NULL COMMENT '本站活动 UUID',
  viewer_id BIGINT NOT NULL COMMENT '观众 viewer.id',
  entitlement_status INT NOT NULL DEFAULT 1 COMMENT '1 有效 / 0 无权益',
  employee_inviter_id BIGINT NULL COMMENT '员工邀请人 rs_mainkey_id',
  employee_invite_time DATETIME(3) NULL COMMENT '员工邀请时间',
  audience_inviter_id BIGINT NULL COMMENT '观众邀请人 viewer.id',
  audience_invite_time DATETIME(3) NULL COMMENT '观众邀请时间',
  importer_id BIGINT NULL COMMENT '导入/添加操作员工 id',
  import_time DATETIME(3) NULL COMMENT '导入/添加时间',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_audience (activity_id, viewer_id),
  KEY idx_activity_audience_activity (activity_id),
  KEY idx_activity_audience_viewer (viewer_id),
  CONSTRAINT fk_activity_audience_activity FOREIGN KEY (activity_id) REFERENCES activity (id),
  CONSTRAINT fk_activity_audience_viewer FOREIGN KEY (viewer_id) REFERENCES viewer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='本场观众运营行；邀请人双字段 + 导入人独立';

CREATE TABLE activity_comment (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '评论主键',
  activity_id VARCHAR(32) NOT NULL COMMENT '本站活动 UUID',
  viewer_id BIGINT NULL COMMENT '观众 viewer.id，可空',
  guest_id VARCHAR(64) NULL COMMENT '未打通 viewer 时的 guest',
  content VARCHAR(500) NOT NULL COMMENT '评论文本',
  nickname VARCHAR(64) NULL COMMENT '发送时昵称快照',
  role_name INT NULL COMMENT '微吼聊天角色数字',
  provider_msg_id VARCHAR(64) NULL COMMENT '第三方消息 id，有则幂等',
  comment_time DATETIME(3) NOT NULL COMMENT '评论时间',
  deleted_time DATETIME(3) NULL COMMENT '软删时间',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '入库时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_comment_provider (activity_id, provider_msg_id),
  KEY idx_activity_comment_activity_time (activity_id, comment_time),
  KEY idx_activity_comment_viewer (activity_id, viewer_id),
  CONSTRAINT fk_activity_comment_activity FOREIGN KEY (activity_id) REFERENCES activity (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='本站直播评论流水；live-web 上报';
