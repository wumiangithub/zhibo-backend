-- 头像与其它观众上传一样，业务侧同时留下访问地址和 TOS 对象键。

ALTER TABLE viewer
  ADD COLUMN avatar_object_key VARCHAR(1024) NULL COMMENT '头像 TOS 对象键，须以 viewer/avatar/ 开头' AFTER avatar_url;
