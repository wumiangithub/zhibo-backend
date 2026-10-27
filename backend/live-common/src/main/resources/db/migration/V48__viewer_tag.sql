-- D80 / T39：顶栏用户标签管理

CREATE TABLE viewer_tag_group (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '分组主键',
  name VARCHAR(32) NOT NULL COMMENT '分组名',
  is_default INT NOT NULL DEFAULT 0 COMMENT '1 系统默认分组',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '排序，小在前',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) NULL COMMENT '软删时间',
  PRIMARY KEY (id),
  KEY idx_viewer_tag_group_sort (sort_order, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='观众标签分组';

INSERT INTO viewer_tag_group (name, is_default, sort_order)
VALUES ('默认分组', 1, 0);

CREATE TABLE viewer_tag (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '标签主键',
  name VARCHAR(20) NOT NULL COMMENT '标签名',
  type INT NOT NULL COMMENT '1 手动 / 2 自动',
  group_id BIGINT NOT NULL COMMENT 'viewer_tag_group.id',
  definition_summary VARCHAR(512) NULL COMMENT '自动标签条件摘要；手动为空',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  deleted_time DATETIME(3) NULL COMMENT '软删时间',
  deleted_by BIGINT NULL COMMENT '软删操作人 rs_mainkey_id',
  deleted_by_name VARCHAR(50) NULL COMMENT '软删操作人姓名快照',
  PRIMARY KEY (id),
  KEY idx_viewer_tag_group (group_id),
  KEY idx_viewer_tag_type (type),
  KEY idx_viewer_tag_name (name),
  KEY idx_viewer_tag_created (created_time),
  CONSTRAINT fk_viewer_tag_group FOREIGN KEY (group_id) REFERENCES viewer_tag_group (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='观众标签';

CREATE TABLE viewer_tag_member (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '打标关系主键',
  tag_id BIGINT NOT NULL COMMENT 'viewer_tag.id',
  viewer_id BIGINT NOT NULL COMMENT 'viewer.id',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '打标时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_viewer_tag_member (tag_id, viewer_id),
  KEY idx_viewer_tag_member_viewer (viewer_id),
  CONSTRAINT fk_vtm_tag FOREIGN KEY (tag_id) REFERENCES viewer_tag (id),
  CONSTRAINT fk_vtm_viewer FOREIGN KEY (viewer_id) REFERENCES viewer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='观众标签打标关系';
