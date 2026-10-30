-- D88：activity_record.status 字符串枚举 → INT（对齐 material 审核等）
-- 1 pending_package / 2 packaging / 3 downloading / 4 ready / 5 failed
-- 残留 pending_download → 1

UPDATE activity_record
SET status = CASE TRIM(status)
      WHEN 'pending_package' THEN '1'
      WHEN 'packaging' THEN '2'
      WHEN 'downloading' THEN '3'
      WHEN 'ready' THEN '4'
      WHEN 'failed' THEN '5'
      WHEN 'pending_download' THEN '1'
      WHEN '1' THEN '1'
      WHEN '2' THEN '2'
      WHEN '3' THEN '3'
      WHEN '4' THEN '4'
      WHEN '5' THEN '5'
      ELSE '1'
    END;

ALTER TABLE activity_record
  MODIFY COLUMN status INT NOT NULL
    COMMENT '1 pending_package / 2 packaging / 3 downloading / 4 ready / 5 failed';
