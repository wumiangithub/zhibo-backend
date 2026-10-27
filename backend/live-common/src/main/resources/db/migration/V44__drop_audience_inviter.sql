-- D77：不记录观众邀请；删除遗留列

ALTER TABLE activity_audience
  DROP COLUMN audience_inviter_id,
  DROP COLUMN audience_invite_time;
