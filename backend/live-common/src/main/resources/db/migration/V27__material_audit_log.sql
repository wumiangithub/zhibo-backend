-- 素材 AI / 风控审核流水（审核记录侧栏）
-- COLLATE 必须与 material 一致（V8 为 utf8mb4_unicode_ci）。
-- 若省略 COLLATE，MySQL 8 默认 utf8mb4_0900_ai_ci，FK material_id→material.id 会报 ERROR 3780。

CREATE TABLE material_audit (
  id VARCHAR(36) PRIMARY KEY,
  material_id VARCHAR(36) NOT NULL,
  action VARCHAR(16) NOT NULL COMMENT 'ai / risk',
  result VARCHAR(16) NOT NULL COMMENT 'passed / rejected',
  operator_id BIGINT NOT NULL,
  operator_name VARCHAR(50) NOT NULL,
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  INDEX idx_material_audit_material_time (material_id, created_time),
  CONSTRAINT fk_material_audit_material FOREIGN KEY (material_id) REFERENCES material(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='素材 AI/风控审核记录';
