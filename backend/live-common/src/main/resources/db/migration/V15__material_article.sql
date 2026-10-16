-- 素材中心文章：正文单独成表，避免列表查出大字段。
-- 文章行复用 material（type=article），不占 TOS object_key。

ALTER TABLE material
  MODIFY COLUMN url VARCHAR(2048) NULL COMMENT '可访问 URL；文章为空',
  MODIFY COLUMN object_key VARCHAR(1024) NULL COMMENT 'TOS 对象 key；文章为空';

CREATE TABLE material_article (
  id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '同 material.id',
  author VARCHAR(16) NOT NULL COMMENT '文章作者',
  content MEDIUMTEXT NOT NULL COMMENT '文章 HTML，上限 100000 字符',
  CONSTRAINT fk_material_article FOREIGN KEY (id) REFERENCES material (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='文章正文；列表不查此表';

INSERT INTO material_group (id, type, name, is_default, sort_order) VALUES
  ('a0000000-0000-4000-8000-000000000004', 'article', '默认分组', 1, 0);
