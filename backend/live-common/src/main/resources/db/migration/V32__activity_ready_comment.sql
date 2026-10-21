-- 无结构变更；V31 ready 保持 TINYINT，实体用 columnDefinition 对齐 Hibernate validate。
-- 本版仅补注释，避免空迁移被误删。
ALTER TABLE activity
  MODIFY COLUMN ready TINYINT NOT NULL DEFAULT 1
    COMMENT '1就绪；0未就绪(转码中)；2失败不可重试';
