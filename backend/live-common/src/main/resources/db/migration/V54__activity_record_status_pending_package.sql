-- D88 状态机：pending_download → pending_package（无 enum 约束，仅迁存量字符串）

UPDATE activity_record
SET status = 'pending_package'
WHERE status = 'pending_download';
