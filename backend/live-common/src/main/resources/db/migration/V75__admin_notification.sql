-- 管理端通知中心（D121）：正文 + 收件箱（写入时按相关人插行，read_time 空=未读）

CREATE TABLE admin_notification (
  id VARCHAR(32) NOT NULL PRIMARY KEY,
  type INT NOT NULL COMMENT '1 直播状态异常 / 2 报备审核 / 3 直播审核 / 4 素材审核',
  audit_result INT NOT NULL COMMENT '0 无 / 1 已通过 / 2 已驳回',
  category VARCHAR(32) NOT NULL COMMENT '展示分类',
  title VARCHAR(64) NOT NULL COMMENT '展示标题',
  body VARCHAR(512) NOT NULL COMMENT '冒号后正文',
  ref_type INT NOT NULL COMMENT '1 活动 / 2 报备 / 3 素材',
  ref_id VARCHAR(32) NOT NULL COMMENT '关联业务 UUID',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_admin_notification_created (created_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='管理端通知正文';

CREATE TABLE admin_notification_inbox (
  notification_id VARCHAR(32) NOT NULL,
  staff_id BIGINT NOT NULL COMMENT '收件人员工 rs_mainkey_id',
  read_time DATETIME(3) NULL COMMENT '已读时刻；空=未读',
  PRIMARY KEY (notification_id, staff_id),
  INDEX idx_inbox_staff_unread (staff_id, read_time),
  CONSTRAINT fk_inbox_notification FOREIGN KEY (notification_id) REFERENCES admin_notification(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='管理端通知收件箱';
