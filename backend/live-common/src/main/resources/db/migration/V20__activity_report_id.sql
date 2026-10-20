-- T29.6：活动绑定已通过直播报备
-- 不改 V16–V19。

ALTER TABLE activity
  ADD COLUMN report_id VARCHAR(36) NULL
    COMMENT '绑定的已通过直播报备 ID；一条报备可绑多场'
    AFTER id;

CREATE INDEX idx_activity_report_id ON activity (report_id);

ALTER TABLE live_report
  MODIFY COLUMN bound_live_count INT NOT NULL DEFAULT 0
    COMMENT '已绑定且未软删的直播场次数';
