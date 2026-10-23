-- 回填 activity_member 历史空 nickname / avatar_url（从 sys_useraccount 快照）
-- 对齐 Java：nickname ← LEFT(TRIM(user_name), 10)；avatar ← headimgurl 优先，其次 user_avatar
-- 只补空（NULL 或空/空白串）；已有本场值不覆盖

UPDATE activity_member am
INNER JOIN sys_useraccount u ON u.rs_mainkey_id = am.user_id
SET
  am.nickname = CASE
    WHEN (am.nickname IS NULL OR TRIM(am.nickname) = '')
         AND u.user_name IS NOT NULL
         AND TRIM(u.user_name) <> ''
      THEN LEFT(TRIM(u.user_name), 10)
    ELSE am.nickname
  END,
  am.avatar_url = CASE
    WHEN am.avatar_url IS NULL OR TRIM(am.avatar_url) = ''
      THEN COALESCE(NULLIF(TRIM(u.headimgurl), ''), NULLIF(TRIM(u.user_avatar), ''))
    ELSE am.avatar_url
  END
WHERE (am.nickname IS NULL OR TRIM(am.nickname) = '')
   OR (am.avatar_url IS NULL OR TRIM(am.avatar_url) = '');
