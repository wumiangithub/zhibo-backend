-- D74：业务 UUID 全量去横线，列 VARCHAR(36) → VARCHAR(32)
-- 先 REPLACE 再缩短列宽，避免 STRICT 截断。

SET FOREIGN_KEY_CHECKS = 0;

-- activity
UPDATE activity SET id = REPLACE(id, '-', '') WHERE id LIKE '%-%';
UPDATE activity SET report_id = REPLACE(report_id, '-', '') WHERE report_id LIKE '%-%';
UPDATE activity SET record_material_id = REPLACE(record_material_id, '-', '') WHERE record_material_id LIKE '%-%';

UPDATE activity_owner SET activity_id = REPLACE(activity_id, '-', '') WHERE activity_id LIKE '%-%';
UPDATE activity_product SET activity_id = REPLACE(activity_id, '-', '') WHERE activity_id LIKE '%-%';
UPDATE activity_audit SET id = REPLACE(id, '-', '') WHERE id LIKE '%-%';
UPDATE activity_audit SET activity_id = REPLACE(activity_id, '-', '') WHERE activity_id LIKE '%-%';
UPDATE activity_courseware SET activity_id = REPLACE(activity_id, '-', '') WHERE activity_id LIKE '%-%';
UPDATE activity_courseware SET material_id = REPLACE(material_id, '-', '') WHERE material_id LIKE '%-%';
UPDATE activity_member SET activity_id = REPLACE(activity_id, '-', '') WHERE activity_id LIKE '%-%';

-- material
UPDATE material_group SET id = REPLACE(id, '-', '') WHERE id LIKE '%-%';
UPDATE material SET id = REPLACE(id, '-', '') WHERE id LIKE '%-%';
UPDATE material SET group_id = REPLACE(group_id, '-', '') WHERE group_id LIKE '%-%';
UPDATE material_article SET id = REPLACE(id, '-', '') WHERE id LIKE '%-%';
UPDATE material_audit SET id = REPLACE(id, '-', '') WHERE id LIKE '%-%';
UPDATE material_audit SET material_id = REPLACE(material_id, '-', '') WHERE material_id LIKE '%-%';

-- live_report
UPDATE live_report SET id = REPLACE(id, '-', '') WHERE id LIKE '%-%';
UPDATE live_report SET cover_material_id = REPLACE(cover_material_id, '-', '') WHERE cover_material_id LIKE '%-%';
UPDATE live_report_audit SET id = REPLACE(id, '-', '') WHERE id LIKE '%-%';
UPDATE live_report_audit SET report_id = REPLACE(report_id, '-', '') WHERE report_id LIKE '%-%';
UPDATE live_report_plan SET report_id = REPLACE(report_id, '-', '') WHERE report_id LIKE '%-%';

-- course
UPDATE course SET id = REPLACE(id, '-', '') WHERE id LIKE '%-%';
UPDATE course SET cover_material_id = REPLACE(cover_material_id, '-', '') WHERE cover_material_id LIKE '%-%';
UPDATE course_owner SET course_id = REPLACE(course_id, '-', '') WHERE course_id LIKE '%-%';
UPDATE course_material SET course_id = REPLACE(course_id, '-', '') WHERE course_id LIKE '%-%';
UPDATE course_material SET material_id = REPLACE(material_id, '-', '') WHERE material_id LIKE '%-%';
UPDATE course_audit SET id = REPLACE(id, '-', '') WHERE id LIKE '%-%';
UPDATE course_audit SET course_id = REPLACE(course_id, '-', '') WHERE course_id LIKE '%-%';

-- attendance / presence
UPDATE room_attendance SET id = REPLACE(id, '-', '') WHERE id LIKE '%-%';
UPDATE room_attendance SET room_id = REPLACE(room_id, '-', '') WHERE room_id LIKE '%-%';
UPDATE presence_event SET id = REPLACE(id, '-', '') WHERE id LIKE '%-%';
UPDATE presence_event SET room_id = REPLACE(room_id, '-', '') WHERE room_id LIKE '%-%';

-- shorten columns
ALTER TABLE activity
  MODIFY COLUMN id VARCHAR(32) NOT NULL COMMENT '本站活动主键 UUID（32 位无横线），对外 API / 路由使用，不是微吼 webinar_id',
  MODIFY COLUMN report_id VARCHAR(32) NULL COMMENT '绑定报备 UUID；live_report.id',
  MODIFY COLUMN record_material_id VARCHAR(32) NULL COMMENT '录播素材 UUID；materialId 路径写入，供转码回调挂钩';

ALTER TABLE activity_owner
  MODIFY COLUMN activity_id VARCHAR(32) NOT NULL COMMENT '本站活动 UUID';

ALTER TABLE activity_product
  MODIFY COLUMN activity_id VARCHAR(32) NOT NULL COMMENT '本站活动 UUID';

ALTER TABLE activity_audit
  MODIFY COLUMN id VARCHAR(32) NOT NULL COMMENT '审核记录 UUID',
  MODIFY COLUMN activity_id VARCHAR(32) NOT NULL COMMENT '本站活动 UUID';

ALTER TABLE activity_courseware
  MODIFY COLUMN activity_id VARCHAR(32) NOT NULL COMMENT '本站活动 UUID',
  MODIFY COLUMN material_id VARCHAR(32) NOT NULL COMMENT '本站素材 UUID';

ALTER TABLE activity_member
  MODIFY COLUMN activity_id VARCHAR(32) NOT NULL COMMENT '本站活动 UUID';

ALTER TABLE material_group
  MODIFY COLUMN id VARCHAR(32) NOT NULL COMMENT '本站分组 UUID';

ALTER TABLE material
  MODIFY COLUMN id VARCHAR(32) NOT NULL COMMENT '本站素材 UUID',
  MODIFY COLUMN group_id VARCHAR(32) NOT NULL COMMENT '所属分组 UUID';

ALTER TABLE material_article
  MODIFY COLUMN id VARCHAR(32) NOT NULL COMMENT '同 material.id';

ALTER TABLE material_audit
  MODIFY COLUMN id VARCHAR(32) NOT NULL,
  MODIFY COLUMN material_id VARCHAR(32) NOT NULL;

ALTER TABLE live_report
  MODIFY COLUMN id VARCHAR(32) NOT NULL COMMENT '报备 UUID',
  MODIFY COLUMN cover_material_id VARCHAR(32) NULL COMMENT '素材中心图片 UUID；旧 cover/ 前缀可空';

ALTER TABLE live_report_audit
  MODIFY COLUMN id VARCHAR(32) NOT NULL COMMENT '审核记录 UUID',
  MODIFY COLUMN report_id VARCHAR(32) NOT NULL COMMENT '报备 UUID';

ALTER TABLE live_report_plan
  MODIFY COLUMN report_id VARCHAR(32) NOT NULL COMMENT '报备 UUID，live_report.id';

ALTER TABLE course
  MODIFY COLUMN id VARCHAR(32) NOT NULL COMMENT '本站课程 UUID',
  MODIFY COLUMN cover_material_id VARCHAR(32) NOT NULL COMMENT '封面素材 UUID';

ALTER TABLE course_owner
  MODIFY COLUMN course_id VARCHAR(32) NOT NULL COMMENT '课程 UUID';

ALTER TABLE course_material
  MODIFY COLUMN course_id VARCHAR(32) NOT NULL COMMENT '课程 UUID',
  MODIFY COLUMN material_id VARCHAR(32) NOT NULL COMMENT '本站素材 UUID';

ALTER TABLE course_audit
  MODIFY COLUMN id VARCHAR(32) NOT NULL COMMENT '审核记录 UUID',
  MODIFY COLUMN course_id VARCHAR(32) NOT NULL COMMENT '课程 UUID';

ALTER TABLE room_attendance
  MODIFY COLUMN id VARCHAR(32) NOT NULL COMMENT '本站进出记录 UUID。进入接口返回这个 id',
  MODIFY COLUMN room_id VARCHAR(32) NOT NULL COMMENT '本站活动 UUID，对应 activity.id。不是微吼 webinar_id';

ALTER TABLE presence_event
  MODIFY COLUMN id VARCHAR(32) NOT NULL,
  MODIFY COLUMN room_id VARCHAR(32) NOT NULL;

SET FOREIGN_KEY_CHECKS = 1;
