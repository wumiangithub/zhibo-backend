-- 素材 AI / 风控合规审核状态（D46）

ALTER TABLE material
  ADD COLUMN ai_audit_status VARCHAR(16) NOT NULL DEFAULT 'pending'
    COMMENT 'AI 审核：pending / passed / rejected' AFTER status,
  ADD COLUMN risk_audit_status VARCHAR(16) NOT NULL DEFAULT 'pending'
    COMMENT '风控合规：pending / passed / rejected' AFTER ai_audit_status;

UPDATE material
SET ai_audit_status = 'pending',
    risk_audit_status = 'pending'
WHERE ai_audit_status IS NULL
   OR risk_audit_status IS NULL
   OR ai_audit_status = ''
   OR risk_audit_status = '';
