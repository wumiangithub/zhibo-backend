-- 课程学员权益到期与获取方式（D100）

ALTER TABLE course_user
  ADD COLUMN entitlement_expire_time DATETIME(3) NULL
    COMMENT '权益到期；空=未设/长期入表；售卖改回长期不清除' AFTER entitlement_status,
  ADD COLUMN acquire_way VARCHAR(16) NOT NULL DEFAULT 'purchase'
    COMMENT 'purchase 购买 / import 导入' AFTER entitlement_expire_time;
