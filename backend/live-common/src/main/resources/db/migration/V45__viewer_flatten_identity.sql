-- D78：取消 viewer_identity；小程序凭证并入 viewer。
-- 登录按 mini_openid；unionid 有则写、没带回不清空。

ALTER TABLE viewer
  ADD COLUMN mini_openid VARCHAR(64) NULL COMMENT '微信小程序 openid；本轮登录查找键' AFTER id,
  ADD COLUMN unionid VARCHAR(64) NULL COMMENT '微信 unionid；有则写，没带回不清空' AFTER mini_openid,
  ADD UNIQUE KEY uk_viewer_mini_openid (mini_openid),
  ADD KEY idx_viewer_unionid (unionid);

UPDATE viewer v
INNER JOIN (
  SELECT i.viewer_id AS viewer_id, i.openid AS openid, i.unionid AS unionid
  FROM viewer_identity i
  INNER JOIN (
    SELECT viewer_id, MIN(id) AS id
    FROM viewer_identity
    WHERE channel = 1
    GROUP BY viewer_id
  ) pick ON pick.id = i.id
) src ON src.viewer_id = v.id
SET v.mini_openid = src.openid,
    v.unionid = src.unionid;

DROP TABLE viewer_identity;
