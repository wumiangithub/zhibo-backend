-- 云 id 列去 provider_ 前缀 + 冗余 provider/room_id（列表可不 join activity）
-- provider_msg_id → msg_id；provider_question_id → question_id
-- 评论表用 room_id（云房间号）；activity 仍为 provider_room_id

ALTER TABLE room_comment
  DROP INDEX uk_room_comment_provider,
  DROP INDEX uk_room_comment_question;

ALTER TABLE room_comment
  CHANGE COLUMN provider_msg_id msg_id VARCHAR(64) NULL COMMENT '云消息 id（如微吼 msg_id；聊天历史/提问 create 后可补）',
  CHANGE COLUMN provider_question_id question_id VARCHAR(64) NULL COMMENT '云提问 id（如微吼 data.id；提问幂等与回答对齐键）';

ALTER TABLE room_comment
  ADD COLUMN provider VARCHAR(32) NULL COMMENT '直播云：vhall / aliyun / tencent（同 activity.provider）' AFTER activity_id,
  ADD COLUMN room_id VARCHAR(64) NULL COMMENT '云房间号（同 activity.provider_room_id；微吼 webinar_id）' AFTER provider;

UPDATE room_comment c
  INNER JOIN activity a ON a.id = c.activity_id
SET c.provider = a.provider,
    c.room_id = a.provider_room_id
WHERE c.provider IS NULL OR c.room_id IS NULL;

ALTER TABLE room_comment
  ADD UNIQUE KEY uk_room_comment_msg (activity_id, msg_id),
  ADD UNIQUE KEY uk_room_comment_question (activity_id, question_id),
  ADD KEY idx_room_comment_provider_room (provider, room_id);
