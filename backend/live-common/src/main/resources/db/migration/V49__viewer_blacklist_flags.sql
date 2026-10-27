-- D82 / T41：用户黑名单三开关（禁止评论 / 购买 / 访问）

ALTER TABLE viewer
  ADD COLUMN ban_comment INT NOT NULL DEFAULT 0 COMMENT '禁止评论 0否1是' AFTER status,
  ADD COLUMN ban_purchase INT NOT NULL DEFAULT 0 COMMENT '禁止购买 0否1是' AFTER ban_comment,
  ADD COLUMN ban_access INT NOT NULL DEFAULT 0 COMMENT '禁止访问 0否1是' AFTER ban_purchase;

-- 既有 status=0 视为已禁止访问
UPDATE viewer SET ban_access = 1 WHERE status = 0 AND ban_access = 0;
