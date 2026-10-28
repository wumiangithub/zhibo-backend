-- 用户运营 · 批量导入历史（失败明细 JSON，保存本次全部失败行）

CREATE TABLE activity_audience_import (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '导入批次主键',
  activity_id VARCHAR(32) NOT NULL COMMENT '本站活动 UUID',
  importer_id BIGINT NOT NULL COMMENT '导入员工 id',
  import_time DATETIME(3) NOT NULL COMMENT '导入时间',
  success_count INT NOT NULL DEFAULT 0 COMMENT '成功数 = created+updated+skipped',
  fail_count INT NOT NULL DEFAULT 0 COMMENT '失败数',
  fail_details MEDIUMTEXT NULL COMMENT '失败明细 JSON [{row,phone,reason},...] 全量',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '入库时间',
  PRIMARY KEY (id),
  KEY idx_audience_import_activity_time (activity_id, import_time),
  CONSTRAINT fk_audience_import_activity FOREIGN KEY (activity_id) REFERENCES activity (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='本场观众批量导入历史';
