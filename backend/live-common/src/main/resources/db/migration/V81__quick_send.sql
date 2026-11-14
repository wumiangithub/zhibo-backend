-- 快捷发送话术库（D126）：分组 + 消息（软删）；租户全局，所有场次共用

CREATE TABLE quick_send_group (
  id VARCHAR(32) NOT NULL PRIMARY KEY COMMENT '本站分组 UUID',
  name VARCHAR(64) NOT NULL COMMENT '分组名称',
  is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=默认分组，不可删',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序，升序',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  UNIQUE KEY uk_quick_send_group_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='快捷发送分组；「全部」不入库';

CREATE TABLE quick_send_message (
  id VARCHAR(32) NOT NULL PRIMARY KEY COMMENT '本站消息 UUID',
  group_id VARCHAR(32) NOT NULL COMMENT '所属分组 UUID',
  content VARCHAR(100) NOT NULL COMMENT '话术正文（可含表情令牌）',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '组内排序，升序',
  deleted_time DATETIME(3) NULL COMMENT '软删时间；非空则列表不展示',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_quick_send_msg_group_sort (group_id, sort_order, id),
  KEY idx_quick_send_msg_deleted (deleted_time),
  CONSTRAINT fk_quick_send_msg_group FOREIGN KEY (group_id) REFERENCES quick_send_group (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='快捷发送话术；软删';

INSERT INTO quick_send_group (id, name, is_default, sort_order) VALUES
  ('b0000000000000000000000000000001', '默认', 1, 0);
