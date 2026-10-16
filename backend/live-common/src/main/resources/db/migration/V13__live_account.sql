-- 直播子系统账号启停（D48）；不改公用 sys_useraccount

CREATE TABLE live_account (
  user_id BIGINT NOT NULL COMMENT 'sys_useraccount.rs_mainkey_id',
  enabled TINYINT NOT NULL DEFAULT 1 COMMENT '1启用 0禁用；无行亦视为启用',
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  updated_by BIGINT NULL COMMENT '操作人 sys_useraccount.rs_mainkey_id',
  PRIMARY KEY (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='直播账号启停（本站子系统）';
