-- D113：直播间已发券关系表 + room_comment 支持优惠券/图片 JSON 快照

ALTER TABLE room_comment
  MODIFY COLUMN content VARCHAR(2000) NOT NULL;

CREATE TABLE activity_coupon (
  id BIGINT NOT NULL AUTO_INCREMENT,
  activity_id VARCHAR(32) NOT NULL,
  coupon_id BIGINT NOT NULL,
  coupon_no VARCHAR(64) NOT NULL,
  coupon_name VARCHAR(256) NOT NULL,
  coupon_amount VARCHAR(32) NOT NULL,
  coupon_start_date VARCHAR(32) NOT NULL,
  coupon_end_date VARCHAR(32) NOT NULL,
  all_sku TINYINT(1) NOT NULL DEFAULT 0,
  dept_names VARCHAR(512) NULL,
  sku_names VARCHAR(1024) NULL,
  last_sender_employee_id BIGINT NOT NULL,
  last_sender_nickname VARCHAR(64) NULL,
  last_sent_time DATETIME(3) NOT NULL,
  created_time DATETIME(3) NOT NULL,
  updated_time DATETIME(3) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_activity_coupon (activity_id, coupon_id),
  KEY idx_activity_coupon_activity_sent (activity_id, last_sent_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
