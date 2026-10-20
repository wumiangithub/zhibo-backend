-- 课程（D53）：独立于直播。新建一律待审核、待上架。价格与章节本轮不建列。

CREATE TABLE course (
  id VARCHAR(36) NOT NULL COMMENT '本站课程 UUID',
  title VARCHAR(45) NOT NULL COMMENT '课程名称',
  introduction VARCHAR(256) NULL COMMENT '课程简介',
  detail_html MEDIUMTEXT NULL COMMENT '课程详情 HTML',
  cover_material_id VARCHAR(36) NOT NULL COMMENT '封面素材 UUID',
  cover_url VARCHAR(2048) NOT NULL COMMENT '绑定时封面 URL 快照',
  auto_list BOOLEAN NOT NULL COMMENT '审核通过后是否自动上架',
  shelf_status VARCHAR(16) NOT NULL COMMENT 'pending 待上架 / on 已上架 / off 已下架 / banned 强制封禁',
  risk_status VARCHAR(16) NOT NULL COMMENT 'pending 待审核 / passed 已通过 / rejected 已驳回',
  content_count INT NOT NULL DEFAULT 0 COMMENT '小节数，本轮恒 0',
  entitled_user_count INT NOT NULL DEFAULT 0 COMMENT '权益用户数，本轮恒 0',
  total_user_count INT NOT NULL DEFAULT 0 COMMENT '累计用户数，本轮恒 0',
  sales_count INT NOT NULL DEFAULT 0 COMMENT '总销量，本轮恒 0',
  listed_at DATETIME(3) NULL COMMENT '最近一次变为已上架的时间',
  created_at DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_at DATETIME(3) NOT NULL COMMENT '最近修改时间',
  deleted_at DATETIME(3) NULL COMMENT '软删时间',
  PRIMARY KEY (id),
  KEY idx_course_updated (deleted_at, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='课程';

CREATE TABLE course_owner (
  course_id VARCHAR(36) NOT NULL COMMENT '课程 UUID',
  user_id BIGINT NOT NULL COMMENT '员工 sys_useraccount.rs_mainkey_id',
  sort_order INT NOT NULL COMMENT '提交顺序，从 0 起',
  PRIMARY KEY (course_id, user_id),
  CONSTRAINT fk_course_owner_course FOREIGN KEY (course_id) REFERENCES course (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='课程归属人；姓名按员工表实时查';

CREATE TABLE course_material (
  course_id VARCHAR(36) NOT NULL COMMENT '课程 UUID',
  material_id VARCHAR(36) NOT NULL COMMENT '本站素材 UUID',
  name VARCHAR(256) NOT NULL COMMENT '绑定时素材名称快照',
  url VARCHAR(2048) NOT NULL COMMENT '绑定时素材 URL 快照',
  type VARCHAR(16) NOT NULL COMMENT 'image / video / document',
  file_size BIGINT NULL COMMENT '绑定时大小快照',
  mime_type VARCHAR(128) NULL COMMENT '绑定时 MIME 快照',
  sort_order INT NOT NULL COMMENT '提交顺序，从 0 起',
  PRIMARY KEY (course_id, material_id),
  CONSTRAINT fk_course_material_course FOREIGN KEY (course_id) REFERENCES course (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='课程课件；不拷贝 TOS';

CREATE TABLE course_audit (
  id VARCHAR(36) NOT NULL COMMENT '审核记录 UUID',
  course_id VARCHAR(36) NOT NULL COMMENT '课程 UUID',
  action VARCHAR(16) NOT NULL COMMENT 'submit 发起申请 / audit 审核',
  result VARCHAR(16) NULL COMMENT 'passed / rejected；submit 为空',
  reason VARCHAR(200) NULL COMMENT '驳回理由',
  operator_id BIGINT NOT NULL COMMENT '操作人员工 id',
  operator_name VARCHAR(50) NOT NULL COMMENT '操作人姓名快照',
  created_at DATETIME(3) NOT NULL COMMENT '记录时间',
  PRIMARY KEY (id),
  KEY idx_course_audit_course (course_id, created_at),
  CONSTRAINT fk_course_audit_course FOREIGN KEY (course_id) REFERENCES course (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='课程风控审核流水';
