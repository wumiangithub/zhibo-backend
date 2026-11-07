-- D113 补：发券人部门（岗位表 hr_uimsyhgwxx.bmid → sys_uimsbmxx），领券传订单中心
ALTER TABLE activity_coupon
  ADD COLUMN last_sender_dept_id BIGINT NULL COMMENT '最近发送员工部门 id（sys_uimsbmxx.rs_mainkey_id）' AFTER last_sender_nickname,
  ADD COLUMN last_sender_dept_name VARCHAR(60) NULL COMMENT '最近发送员工部门名（sys_uimsbmxx.bmmc）' AFTER last_sender_dept_id;
