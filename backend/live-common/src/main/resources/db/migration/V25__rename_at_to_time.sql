-- D59：本站时间列统一 *_at → *_time（不改微吼 signed_at 等第三方字段）

-- activity
ALTER TABLE activity
  RENAME COLUMN created_at TO created_time,
  RENAME COLUMN updated_at TO updated_time,
  RENAME COLUMN deleted_at TO deleted_time;
ALTER TABLE activity RENAME INDEX idx_activity_created TO idx_activity_created_time;
ALTER TABLE activity RENAME INDEX idx_activity_deleted TO idx_activity_deleted_time;

-- activity_audit
ALTER TABLE activity_audit
  RENAME COLUMN created_at TO created_time;
ALTER TABLE activity_audit RENAME INDEX idx_activity_audit_activity_created TO idx_activity_audit_activity_created_time;

-- material_group
ALTER TABLE material_group
  RENAME COLUMN created_at TO created_time,
  RENAME COLUMN updated_at TO updated_time;

-- material
ALTER TABLE material
  RENAME COLUMN deleted_at TO deleted_time,
  RENAME COLUMN created_at TO created_time,
  RENAME COLUMN updated_at TO updated_time;
ALTER TABLE material RENAME INDEX idx_material_created TO idx_material_created_time;

-- room_attendance
ALTER TABLE room_attendance
  RENAME COLUMN created_at TO created_time;

-- presence_event
ALTER TABLE presence_event
  RENAME COLUMN created_at TO created_time;

-- live_account
ALTER TABLE live_account
  RENAME COLUMN updated_at TO updated_time;

-- live_report
ALTER TABLE live_report
  RENAME COLUMN submitted_at TO submitted_time,
  RENAME COLUMN created_at TO created_time,
  RENAME COLUMN updated_at TO updated_time;
ALTER TABLE live_report RENAME INDEX idx_live_report_submitted_at TO idx_live_report_submitted_time;

-- live_report_audit
ALTER TABLE live_report_audit
  RENAME COLUMN created_at TO created_time;

-- course
ALTER TABLE course
  RENAME COLUMN listed_at TO listed_time,
  RENAME COLUMN created_at TO created_time,
  RENAME COLUMN updated_at TO updated_time,
  RENAME COLUMN deleted_at TO deleted_time;

-- course_audit
ALTER TABLE course_audit
  RENAME COLUMN created_at TO created_time;
