-- 课程售卖「固定时间到期」（D100 扩展）

ALTER TABLE course
  ADD COLUMN access_validity_fixed_time DATETIME(3) NULL
    COMMENT '固定到期时刻；access_validity_type=fixed 时有值' AFTER access_validity_days;

ALTER TABLE course
  MODIFY COLUMN access_validity_type VARCHAR(16) NOT NULL DEFAULT 'long'
    COMMENT '访问有效期：long 长期 / days 权益生效后 N 天 / fixed 固定时间到期';
