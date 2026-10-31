-- 课程目录树：章节 / 子课程 / 小节。小节绑定唯一直播或素材。

CREATE TABLE course_catalog_node (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '目录节点自增主键',
  course_id VARCHAR(32) NOT NULL COMMENT '所属课程 UUID',
  parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父节点 id；根下为 0',
  node_type INT NOT NULL COMMENT '1 章节 / 2 子课程 / 3 小节',
  title VARCHAR(30) NOT NULL COMMENT '展示名；小节默认取直播/素材名',
  cover_material_id VARCHAR(32) NULL COMMENT '子课程封面素材 UUID；其它类型空',
  cover_url VARCHAR(2048) NULL COMMENT '子课程封面 URL 快照；其它类型空',
  content_kind INT NULL COMMENT '仅小节：1 直播 / 2 图片 / 3 视频 / 4 文档 / 5 文章；容器空',
  content_id VARCHAR(32) NULL COMMENT '仅小节：活动或素材 UUID；容器空',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '同父同类型内排序，从 0 起',
  created_time DATETIME(3) NOT NULL COMMENT '创建时间',
  updated_time DATETIME(3) NOT NULL COMMENT '最近修改时间',
  deleted_time DATETIME(3) NULL COMMENT '软删时间',
  PRIMARY KEY (id),
  KEY idx_ccn_course_parent_type_sort (course_id, parent_id, node_type, sort_order),
  KEY idx_ccn_course_content (course_id, content_kind, content_id),
  CONSTRAINT fk_ccn_course FOREIGN KEY (course_id) REFERENCES course (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='课程目录节点（章节/子课程/小节）';
