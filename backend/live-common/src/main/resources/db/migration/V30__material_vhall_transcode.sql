-- 素材视频预上传微吼媒资：video_id + 转码进度 + 转码结果。仅 type=video 使用。
-- 转码进度回调：https://vhall.apifox.cn/6838857m0
-- 转码完成（成功/失败）回调 method_id=3：https://vhall.apifox.cn/6838858m0 （status 1成功 / 3失败）

ALTER TABLE material
  ADD COLUMN vhall_video_id VARCHAR(64) NULL
    COMMENT '微吼媒资 video_id；空=未提交转码（待转码）' AFTER department_name,
  ADD COLUMN vhall_transcode_progress INT NULL
    COMMENT '微吼转码进度 0～100；空=未上传；100=成功时对齐' AFTER vhall_video_id,
  ADD COLUMN vhall_transcode_status INT NULL
    COMMENT '转码结果：空=未上传；0=转码中；1=成功；3=失败（对齐微吼完成回调 status）' AFTER vhall_transcode_progress;

CREATE INDEX idx_material_vhall_video_id ON material (vhall_video_id);
