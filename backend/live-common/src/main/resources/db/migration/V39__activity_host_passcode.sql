-- 口令开播：一场一码，全局唯一；H5 链接与以后 App 都凭口令进房
ALTER TABLE activity
  ADD COLUMN host_passcode VARCHAR(16) NULL
    COMMENT '口令开播唯一口令（9 位数字）' AFTER provider_room_id;

-- 存量：用 id 派生 9 位码；碰撞极少，应用侧 get/reset 仍会保证唯一
UPDATE activity
SET host_passcode = LPAD(
  CAST(
    (CONV(SUBSTRING(REPLACE(id, '-', ''), 1, 8), 16, 10) % 900000000) + 100000000
    AS CHAR
  ),
  9,
  '0'
)
WHERE host_passcode IS NULL
  AND id IS NOT NULL
  AND id <> '';

ALTER TABLE activity
  ADD UNIQUE KEY uk_activity_host_passcode (host_passcode);
