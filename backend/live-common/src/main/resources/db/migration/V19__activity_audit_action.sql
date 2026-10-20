-- T30：活动审核记录对齐报备，补 action；历史场次回填「发起申请」
-- 不改 V9 / V16–V18。

ALTER TABLE activity_audit
  ADD COLUMN action VARCHAR(16) NOT NULL DEFAULT 'audit'
    COMMENT 'submit 发起申请 / audit 审核'
    AFTER activity_id;

ALTER TABLE activity_audit
  MODIFY COLUMN result VARCHAR(16) NULL
    COMMENT 'audit 时 passed / rejected；submit 为空';

UPDATE activity_audit
SET action = 'audit'
WHERE action = '' OR action IS NULL;

INSERT INTO activity_audit (id, activity_id, action, result, reason, operator_id, operator_name, created_at)
SELECT LOWER(UUID()), a.id, 'submit', NULL, NULL, 0, '系统', a.created_at
FROM activity a
WHERE NOT EXISTS (
  SELECT 1 FROM activity_audit x
  WHERE x.activity_id = a.id AND x.action = 'submit'
);
