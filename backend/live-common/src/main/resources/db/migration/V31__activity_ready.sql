-- 录播异步就绪：未转码先落本站活动 ready=0，转码成功后再 create-demand。
-- ready：1 就绪（默认）/ 0 转码中未就绪 / 2 失败（不可重试）

ALTER TABLE activity
  ADD COLUMN ready TINYINT NOT NULL DEFAULT 1
    COMMENT '1就绪；0未就绪(转码中)；2失败不可重试' AFTER type,
  ADD COLUMN record_material_id VARCHAR(36) NULL
    COMMENT '录播素材 UUID；materialId 路径写入，供转码回调挂钩' AFTER ready;

CREATE INDEX idx_activity_record_material_ready
  ON activity (record_material_id, ready);
