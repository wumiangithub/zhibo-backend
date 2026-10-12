-- 素材中心：分组 + 素材元数据（文件在火山 TOS，本站只存 URL/key）

CREATE TABLE material_group (
  id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '本站分组 UUID',
  type VARCHAR(16) NOT NULL COMMENT 'image / video / document',
  name VARCHAR(64) NOT NULL COMMENT '分组名称',
  is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '1=该 type 下默认分组，不可删',
  sort_order INT NOT NULL DEFAULT 0 COMMENT '同 type 内排序，升序',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_material_group_type (type),
  UNIQUE KEY uk_material_group_type_name (type, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='素材分组；「全部」为前端虚拟分组不入库';

CREATE TABLE material (
  id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '本站素材 UUID',
  type VARCHAR(16) NOT NULL COMMENT 'image / video / document',
  group_id VARCHAR(36) NOT NULL COMMENT '所属分组 UUID',
  name VARCHAR(256) NOT NULL COMMENT '展示名称，一般取文件名',
  url VARCHAR(2048) NOT NULL COMMENT '可访问 URL（CDN/原生）',
  object_key VARCHAR(1024) NOT NULL COMMENT 'TOS 对象 key，如 material/image/...',
  file_size BIGINT NULL COMMENT '字节数',
  mime_type VARCHAR(128) NULL COMMENT 'MIME，可选',
  meta VARCHAR(128) NULL COMMENT '列表副文案：尺寸/时长/大小等',
  status VARCHAR(16) NOT NULL DEFAULT 'available' COMMENT 'available / unavailable',
  deleted_at DATETIME(3) NULL COMMENT '软删时间；非空则列表不展示',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  KEY idx_material_type_group (type, group_id),
  KEY idx_material_created (created_at),
  KEY idx_material_object_key (object_key(191)),
  CONSTRAINT fk_material_group FOREIGN KEY (group_id) REFERENCES material_group (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='素材元数据；文件在 TOS，本站不落盘';

-- 每类一个默认分组（固定 UUID，便于联调）
INSERT INTO material_group (id, type, name, is_default, sort_order) VALUES
  ('a0000000-0000-4000-8000-000000000001', 'image', '默认分组', 1, 0),
  ('a0000000-0000-4000-8000-000000000002', 'video', '默认分组', 1, 0),
  ('a0000000-0000-4000-8000-000000000003', 'document', '默认分组', 1, 0);
