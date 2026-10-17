-- T29.5：封面 URL/key/头像加长、补 cover_material_id、列注释。
-- 不 CONVERT 整表（会与 fk_report_audit_report 的 id/report_id collation 冲突）。

ALTER TABLE live_report
  ADD COLUMN cover_material_id VARCHAR(36) NULL COMMENT '素材中心图片 UUID；旧 cover/ 前缀可空' AFTER cover_mime_type;

ALTER TABLE live_report
  ADD INDEX idx_live_report_cover_material (cover_material_id);

ALTER TABLE live_report
  MODIFY COLUMN id VARCHAR(36) NOT NULL COMMENT '报备 UUID（客户端生成）',
  MODIFY COLUMN title VARCHAR(64) NOT NULL COMMENT '报备直播名称',
  MODIFY COLUMN content VARCHAR(500) NOT NULL COMMENT '直播报备内容',
  MODIFY COLUMN plan_times JSON NOT NULL COMMENT '计划时间 [{startTime,endTime}]，1～10 段',
  MODIFY COLUMN plan_start_min DATETIME(3) NOT NULL COMMENT '各段开始时间最小值，供列表筛选',
  MODIFY COLUMN plan_end_max DATETIME(3) NOT NULL COMMENT '各段结束时间最大值，供列表筛选',
  MODIFY COLUMN cover_url VARCHAR(2048) NOT NULL COMMENT '封面可访问 URL（来自素材中心快照）',
  MODIFY COLUMN cover_object_key VARCHAR(1024) NOT NULL COMMENT '封面 TOS key，material/image/ 或兼容 live-report/{id}/cover/',
  MODIFY COLUMN cover_file_size BIGINT NOT NULL COMMENT '封面字节数，≤5MB',
  MODIFY COLUMN cover_mime_type VARCHAR(100) NOT NULL COMMENT 'image/jpeg 或 image/png',
  MODIFY COLUMN host_id BIGINT NOT NULL COMMENT '主播 sys_useraccount.rs_mainkey_id',
  MODIFY COLUMN host_name VARCHAR(50) NOT NULL COMMENT '主播姓名快照',
  MODIFY COLUMN host_avatar VARCHAR(2048) NULL COMMENT '主播头像快照（优先微信头像）',
  MODIFY COLUMN attachments JSON NOT NULL COMMENT '报备材料 [{name,url,objectKey,fileSize,mimeType}]，1～20 份',
  MODIFY COLUMN status VARCHAR(16) NOT NULL DEFAULT 'pending' COMMENT 'pending 待审核 / passed 已通过 / rejected 已驳回',
  MODIFY COLUMN bound_live_count INT NOT NULL DEFAULT 0 COMMENT '已绑定直播场次；本期恒 0',
  MODIFY COLUMN submitter_id BIGINT NOT NULL COMMENT '提交人 sys_useraccount.rs_mainkey_id',
  MODIFY COLUMN submitter_name VARCHAR(50) NOT NULL COMMENT '提交人姓名快照',
  MODIFY COLUMN department_name VARCHAR(200) NULL COMMENT '提交人部门快照，多部门顿号',
  MODIFY COLUMN submitted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '最近提交时间',
  MODIFY COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  MODIFY COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间';

ALTER TABLE live_report_audit
  MODIFY COLUMN id VARCHAR(36) NOT NULL COMMENT '审核记录 UUID',
  MODIFY COLUMN report_id VARCHAR(36) NOT NULL COMMENT '报备 UUID',
  MODIFY COLUMN action VARCHAR(16) NOT NULL COMMENT 'submit / resubmit / audit / append',
  MODIFY COLUMN result VARCHAR(16) NULL COMMENT 'audit 时 passed / rejected，其余空',
  MODIFY COLUMN reason VARCHAR(500) NULL COMMENT '驳回原因或追加备注',
  MODIFY COLUMN operator_id BIGINT NOT NULL COMMENT '操作人员工 id',
  MODIFY COLUMN operator_name VARCHAR(50) NOT NULL COMMENT '操作人姓名快照',
  MODIFY COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '操作时间';
