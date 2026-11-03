-- 课程小节学习进度：每课 × 每小节 × 每学员一行（D101）

CREATE TABLE course_lesson_progress (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '行主键，不对外',
  course_id VARCHAR(32) NOT NULL COMMENT '本站课程 UUID',
  lesson_node_id BIGINT NOT NULL COMMENT 'course_catalog_node.id（小节）',
  viewer_id BIGINT NOT NULL COMMENT '观众 viewer.id',
  participate_status INT NOT NULL DEFAULT 0 COMMENT '0 未开始 / 1 进行中 / 2 已完成',
  learn_duration_sec BIGINT NOT NULL DEFAULT 0 COMMENT '累计学习时长（秒）',
  progress_bar_sec INT NULL COMMENT '进度条最远点（秒）；仅直播/视频',
  progress_percent INT NOT NULL DEFAULT 0 COMMENT '0～100',
  completed_time DATETIME(3) NULL COMMENT '首次完成时间',
  first_participate_time DATETIME(3) NULL COMMENT '首次参与时间',
  last_learn_time DATETIME(3) NULL COMMENT '最近上报时间',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_lesson_viewer (course_id, lesson_node_id, viewer_id),
  KEY idx_clp_course_lesson (course_id, lesson_node_id),
  KEY idx_clp_course_viewer (course_id, viewer_id),
  CONSTRAINT fk_clp_course FOREIGN KEY (course_id) REFERENCES course (id),
  CONSTRAINT fk_clp_lesson FOREIGN KEY (lesson_node_id) REFERENCES course_catalog_node (id),
  CONSTRAINT fk_clp_viewer FOREIGN KEY (viewer_id) REFERENCES viewer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='课程小节学习进度（D101）';
