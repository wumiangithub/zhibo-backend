-- 历史主播行（从 activity_host 迁入）tag 为空；与新建默认「讲师」对齐
UPDATE activity_member
SET tag = '讲师'
WHERE role = 1
  AND (tag IS NULL OR TRIM(tag) = '');
