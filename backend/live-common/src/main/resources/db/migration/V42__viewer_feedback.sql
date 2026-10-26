-- 观众意见反馈（小程序「我的 → 意见反馈」）

CREATE TABLE viewer_feedback (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '反馈主键',
  viewer_id BIGINT NOT NULL COMMENT 'viewer.id',
  category INT NOT NULL COMMENT '1课程权益 2视频播放 3学习体验 4其他',
  content VARCHAR(200) NOT NULL COMMENT '意见描述，最多200字',
  contact_phone VARCHAR(20) NULL COMMENT '选填联系手机号，纯数字',
  images_json VARCHAR(4000) NULL COMMENT '截图 JSON 数组 [{url,objectKey}]，最多4张',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '提交时间',
  PRIMARY KEY (id),
  KEY idx_viewer_feedback_viewer (viewer_id),
  KEY idx_viewer_feedback_created (created_time),
  CONSTRAINT fk_viewer_feedback_viewer FOREIGN KEY (viewer_id) REFERENCES viewer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='观众意见反馈';
