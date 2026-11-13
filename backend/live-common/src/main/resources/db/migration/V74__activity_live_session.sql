-- D119：直播开播场次（微吼 get-webinar-switch-list）。一行 = 一场活动 + 一次开/结束。

CREATE TABLE activity_live_session (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '行主键，不对外',
  activity_id VARCHAR(32) NOT NULL COMMENT '本站活动 UUID',
  provider VARCHAR(32) NOT NULL COMMENT 'vhall 等',
  switch_id BIGINT NOT NULL COMMENT '微吼 switch_list[].id；回调临时未闭合段可用 0',
  start_time DATETIME(3) NULL COMMENT '该场实际开始',
  end_time DATETIME(3) NULL COMMENT '该场实际结束；直播中可空',
  session_type INT NULL COMMENT '← switch_list[].type',
  start_type INT NULL COMMENT '← switch_list[].start_type',
  end_type INT NULL COMMENT '← switch_list[].end_type',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '本站写入时间',
  updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '本站更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_live_session (activity_id, provider, switch_id),
  KEY idx_activity_live_session_activity (activity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='开播场次。一行一场活动+一次微吼 switch；时长/峰值按多段并集';
