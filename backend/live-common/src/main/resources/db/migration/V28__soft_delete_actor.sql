-- 软删记录删除人：活动补列；报备新增软删列
-- deleted_by = sys_useraccount.rs_mainkey_id（BIGINT / D56）
-- deleted_by_name = 删除时姓名快照

ALTER TABLE activity
  ADD COLUMN deleted_by BIGINT NULL
    COMMENT '删除人 sys_useraccount.rs_mainkey_id' AFTER deleted_time,
  ADD COLUMN deleted_by_name VARCHAR(50) NULL
    COMMENT '删除人姓名快照' AFTER deleted_by;

ALTER TABLE live_report
  ADD COLUMN deleted_time DATETIME(3) NULL
    COMMENT '软删时间；非空则列表与详情视为不存在' AFTER updated_time,
  ADD COLUMN deleted_by BIGINT NULL
    COMMENT '删除人 sys_useraccount.rs_mainkey_id' AFTER deleted_time,
  ADD COLUMN deleted_by_name VARCHAR(50) NULL
    COMMENT '删除人姓名快照' AFTER deleted_by,
  ADD KEY idx_live_report_deleted_time (deleted_time);
