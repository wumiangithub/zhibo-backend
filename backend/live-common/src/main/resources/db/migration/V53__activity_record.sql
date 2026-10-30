-- D88：直播回放元数据与 TOS 成片落库。一行 = 一场活动 + 一个云侧 record_id。

CREATE TABLE activity_record (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '行主键，不对外',
  activity_id VARCHAR(32) NOT NULL COMMENT '本站活动 UUID',
  provider VARCHAR(32) NOT NULL COMMENT 'vhall / aliyun / tencent',
  record_id BIGINT NOT NULL COMMENT '云侧回放 id',
  title VARCHAR(256) NULL COMMENT '回放名称 ← get-info name',
  duration_sec INT NULL COMMENT '时长秒 ← get-info duration',
  cover_url VARCHAR(1024) NULL COMMENT '封面 ← get-info img_url',
  storage_kb BIGINT NULL COMMENT '体积 KB ← get-info storage',
  status VARCHAR(32) NOT NULL COMMENT 'pending_package / packaging / downloading / ready / failed',
  object_key VARCHAR(1024) NULL COMMENT 'TOS 键；ready 时必有',
  tos_url VARCHAR(1024) NULL COMMENT '可访问 URL；ready 时必有',
  provider_created_time DATETIME(3) NULL COMMENT '云侧生成时间 ← get-info created_at',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '本站写入时间',
  updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '本站更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_record_provider_record (activity_id, provider, record_id),
  KEY idx_activity_record_activity (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='活动回放文件。一行一场活动+一个云侧 record_id；成片落 TOS';
