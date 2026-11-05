-- 问答与聊天同表，用类型区分。存量行都是聊天。

ALTER TABLE room_comment
  ADD COLUMN comment_type INT NOT NULL DEFAULT 1 COMMENT '1 聊天 / 2 提问' AFTER role_name;
