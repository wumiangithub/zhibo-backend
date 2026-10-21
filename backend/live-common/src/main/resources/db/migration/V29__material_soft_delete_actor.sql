-- 素材软删记录删除人（与 activity / live_report 对齐）
-- deleted_by = sys_useraccount.rs_mainkey_id（BIGINT / D56）
-- deleted_by_name = 删除时姓名快照

ALTER TABLE material
  ADD COLUMN deleted_by BIGINT NULL
    COMMENT '删除人 sys_useraccount.rs_mainkey_id' AFTER deleted_time,
  ADD COLUMN deleted_by_name VARCHAR(50) NULL
    COMMENT '删除人姓名快照' AFTER deleted_by;
