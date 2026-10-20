-- 素材 AI / 风控审核改为整数：0 待审核 / 1 已通过 / 2 已驳回

UPDATE material
SET ai_audit_status = CASE TRIM(ai_audit_status)
      WHEN 'passed' THEN '1'
      WHEN 'rejected' THEN '2'
      WHEN '0' THEN '0'
      WHEN '1' THEN '1'
      WHEN '2' THEN '2'
      ELSE '0'
    END,
    risk_audit_status = CASE TRIM(risk_audit_status)
      WHEN 'passed' THEN '1'
      WHEN 'rejected' THEN '2'
      WHEN '0' THEN '0'
      WHEN '1' THEN '1'
      WHEN '2' THEN '2'
      ELSE '0'
    END;

ALTER TABLE material
  MODIFY COLUMN ai_audit_status INT NOT NULL DEFAULT 0
    COMMENT 'AI 审核：0 待审核 / 1 已通过 / 2 已驳回',
  MODIFY COLUMN risk_audit_status INT NOT NULL DEFAULT 0
    COMMENT '风控合规：0 待审核 / 1 已通过 / 2 已驳回';
