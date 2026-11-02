-- 课程学员表：分享链进房 enroll 写入（对齐 activity_audience 骨架）

CREATE TABLE course_user (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '行主键',
  course_id VARCHAR(32) NOT NULL COMMENT '本站课程 UUID',
  viewer_id BIGINT NOT NULL COMMENT '观众 viewer.id',
  entitlement_status INT NOT NULL DEFAULT 1 COMMENT '1 有效 / 0 无权益',
  employee_inviter_id BIGINT NULL COMMENT '员工邀请人 rs_mainkey_id',
  employee_invite_time DATETIME(3) NULL COMMENT '员工邀请时间',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_course_user (course_id, viewer_id),
  KEY idx_course_user_course (course_id),
  KEY idx_course_user_viewer (viewer_id),
  CONSTRAINT fk_course_user_course FOREIGN KEY (course_id) REFERENCES course (id),
  CONSTRAINT fk_course_user_viewer FOREIGN KEY (viewer_id) REFERENCES viewer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='课程学员；分享链 enroll 写入；员工邀请 first-write-wins';
