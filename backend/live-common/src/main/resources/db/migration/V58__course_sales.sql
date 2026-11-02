-- 课程售卖设置（D98）：对齐 activity 售卖字段；自定义时长起算 listed_time（课程上架后 N 天）
-- ADD 带 DEFAULT 回填已有行：单独售卖 + 免费 + 长期有效

ALTER TABLE course
  ADD COLUMN sell_alone BOOLEAN NOT NULL DEFAULT TRUE
    COMMENT '是否单独售卖' AFTER sales_count,
  ADD COLUMN sell_type VARCHAR(16) NOT NULL DEFAULT 'free'
    COMMENT 'free 免费 / encrypted 加密 / specified 指定用户' AFTER sell_alone,
  ADD COLUMN validity_label VARCHAR(32) NOT NULL DEFAULT '长期有效'
    COMMENT '列表售卖有效期文案' AFTER sell_type,
  ADD COLUMN sell_password_cipher VARCHAR(512) NULL
    COMMENT '加密售卖密码 AES 密文' AFTER validity_label,
  ADD COLUMN access_validity_type VARCHAR(16) NOT NULL DEFAULT 'long'
    COMMENT '访问有效期：long 长期 / days 上架后 N 天' AFTER sell_password_cipher,
  ADD COLUMN access_validity_days INT NULL
    COMMENT '上架后 N 天；access_validity_type=days 时有值' AFTER access_validity_type;
