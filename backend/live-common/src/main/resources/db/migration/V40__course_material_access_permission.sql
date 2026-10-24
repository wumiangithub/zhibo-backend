-- 课程课件：学员端用户权限（D75）
ALTER TABLE course_material
  ADD COLUMN access_permission INT NOT NULL DEFAULT 1
    COMMENT '1 预览及下载 / 2 仅预览 / 3 仅下载'
    AFTER mime_type;
