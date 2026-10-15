-- 素材上传人 / 部门快照（D47）

ALTER TABLE material
  ADD COLUMN uploader_id BIGINT NULL
    COMMENT '上传人 sys_useraccount.rs_mainkey_id' AFTER risk_audit_status,
  ADD COLUMN uploader_name VARCHAR(50) NULL
    COMMENT '上传人姓名快照' AFTER uploader_id,
  ADD COLUMN department_name VARCHAR(256) NULL
    COMMENT '所在部门展示名快照（多部门顿号）' AFTER uploader_name;
