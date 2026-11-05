-- 提问 id 与 msg_id 分列；主持人回复落在同一 room_comment 行（D107）。
-- provider_question_id = 微吼 data.id（create/commit/backout 对齐键）
-- provider_msg_id = 微吼 data.msg_id（聊天仍用此列；提问可空，create 后补）

ALTER TABLE room_comment
  ADD COLUMN provider_question_id VARCHAR(64) NULL COMMENT '微吼提问 id（data.id）' AFTER provider_msg_id,
  ADD COLUMN answer_content VARCHAR(500) NULL COMMENT '主持人/嘉宾回复正文' AFTER provider_question_id,
  ADD COLUMN answer_nickname VARCHAR(64) NULL COMMENT '回复人昵称快照' AFTER answer_content,
  ADD COLUMN answer_role_name INT NULL COMMENT '回复人微吼角色数字' AFTER answer_nickname;

-- 若前期误把提问数字 id 写入 provider_msg_id，迁到 provider_question_id 并清空 msg 列
UPDATE room_comment
SET provider_question_id = provider_msg_id,
    provider_msg_id = NULL
WHERE comment_type = 2
  AND provider_question_id IS NULL
  AND provider_msg_id IS NOT NULL
  AND provider_msg_id REGEXP '^[0-9]+$';

ALTER TABLE room_comment
  ADD UNIQUE KEY uk_room_comment_question (activity_id, provider_question_id);
