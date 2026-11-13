-- 头像 objectKey 仅 update 请求校验，不落库；去掉历史列。
ALTER TABLE viewer
  DROP COLUMN avatar_object_key;
