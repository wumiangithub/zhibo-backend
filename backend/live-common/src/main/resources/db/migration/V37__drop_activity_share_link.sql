-- 不再使用微吼分享页。观众观看链接由前端拼接 /watch/{本站UUID}。

ALTER TABLE activity DROP COLUMN share_link;
