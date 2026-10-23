-- D66：activity_member.role 与微吼 get-role-url type 对齐
-- 旧：1 主播 / 2 助理 / 3 嘉宾
-- 新：1 主播 / 2 嘉宾 / 3 助理
-- 用临时值互换，避免同行 UPDATE 互相覆盖

UPDATE activity_member SET role = 92 WHERE role = 2;
UPDATE activity_member SET role = 2 WHERE role = 3;
UPDATE activity_member SET role = 3 WHERE role = 92;

ALTER TABLE activity_member
  MODIFY COLUMN role INT NOT NULL COMMENT '开播角色：1 主播 / 2 嘉宾 / 3 助理（与微吼 get-role-url type 一致）';
