-- 课程学员添加/导入：导入人字段 + 批量导入历史（对齐 activity_audience）

ALTER TABLE course_user
  ADD COLUMN importer_id BIGINT NULL
    COMMENT '添加/导入操作员工 id' AFTER acquire_way,
  ADD COLUMN import_time DATETIME(3) NULL
    COMMENT '添加/导入时间' AFTER importer_id;

CREATE TABLE course_user_import (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '导入批次主键',
  course_id VARCHAR(32) NOT NULL COMMENT '本站课程 UUID',
  importer_id BIGINT NOT NULL COMMENT '导入员工 id',
  import_time DATETIME(3) NOT NULL COMMENT '导入时间',
  success_count INT NOT NULL DEFAULT 0 COMMENT '成功数 = created+updated+skipped',
  fail_count INT NOT NULL DEFAULT 0 COMMENT '失败数',
  fail_details MEDIUMTEXT NULL COMMENT '失败明细 JSON [{row,phone,reason},...] 全量',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '入库时间',
  PRIMARY KEY (id),
  KEY idx_course_user_import_course_time (course_id, import_time),
  CONSTRAINT fk_course_user_import_course FOREIGN KEY (course_id) REFERENCES course (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='课程学员批量导入历史';
