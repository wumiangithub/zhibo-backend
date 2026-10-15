-- 第十一迭代（D44）：自动上架 / 主播 / 课件 / 回放 / 售卖扩展
-- 勿改 V9；已有行回填 auto_list=false、回放开启+永久+倍速/快进允许、售卖保持原 sell_type + 长期

ALTER TABLE activity
  ADD COLUMN auto_list BOOLEAN NOT NULL DEFAULT FALSE
    COMMENT '是否自动上架：风控通过后若为 true 则 shelf=on' AFTER deleted_at,
  ADD COLUMN sell_alone BOOLEAN NOT NULL DEFAULT TRUE
    COMMENT '是否单独售卖' AFTER auto_list,
  ADD COLUMN price DECIMAL(12, 2) NULL
    COMMENT '付费价格（元）；非 paid 为空' AFTER sell_alone,
  ADD COLUMN line_price DECIMAL(12, 2) NULL
    COMMENT '划线价；有值须大于 price' AFTER price,
  ADD COLUMN sell_password_cipher VARCHAR(512) NULL
    COMMENT '加密售卖密码 AES 密文；非 encrypted 为空' AFTER line_price,
  ADD COLUMN access_validity_type VARCHAR(16) NOT NULL DEFAULT 'long'
    COMMENT '访问有效期：long 长期 / days 购买后 N 天' AFTER sell_password_cipher,
  ADD COLUMN access_validity_days INT NULL
    COMMENT '购买后 N 天；access_validity_type=days 时有值' AFTER access_validity_type,
  ADD COLUMN playback_enabled BOOLEAN NOT NULL DEFAULT TRUE
    COMMENT '是否开启回放' AFTER access_validity_days,
  ADD COLUMN playback_validity_type VARCHAR(16) NOT NULL DEFAULT 'forever'
    COMMENT '回放有效期：forever 永久 / days 结束后 N 天' AFTER playback_enabled,
  ADD COLUMN playback_validity_days INT NULL
    COMMENT '直播结束后 N 天；playback_validity_type=days 时有值' AFTER playback_validity_type,
  ADD COLUMN playback_speed_allow BOOLEAN NOT NULL DEFAULT TRUE
    COMMENT '是否允许倍速' AFTER playback_validity_days,
  ADD COLUMN playback_fast_forward_allow BOOLEAN NOT NULL DEFAULT TRUE
    COMMENT '是否允许快进' AFTER playback_speed_allow;

UPDATE activity
SET auto_list = FALSE,
    sell_alone = TRUE,
    access_validity_type = 'long',
    access_validity_days = NULL,
    playback_enabled = TRUE,
    playback_validity_type = 'forever',
    playback_validity_days = NULL,
    playback_speed_allow = TRUE,
    playback_fast_forward_allow = TRUE;

CREATE TABLE activity_host (
  activity_id VARCHAR(36) NOT NULL COMMENT '本站活动 UUID',
  user_id BIGINT NOT NULL COMMENT '员工 sys_useraccount.rs_mainkey_id',
  sort_order INT NOT NULL COMMENT '提交顺序，从 0 起',
  PRIMARY KEY (activity_id, user_id),
  CONSTRAINT fk_activity_host_activity FOREIGN KEY (activity_id) REFERENCES activity (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='活动主播；姓名按员工表实时查；本轮不同步微吼角色';

CREATE TABLE activity_courseware (
  activity_id VARCHAR(36) NOT NULL COMMENT '本站活动 UUID',
  material_id VARCHAR(36) NOT NULL COMMENT '本站素材 UUID',
  name VARCHAR(256) NOT NULL COMMENT '绑定时素材名称快照',
  url VARCHAR(2048) NOT NULL COMMENT '绑定时素材 URL 快照',
  type VARCHAR(16) NOT NULL COMMENT '绑定时类型快照：image/video/document',
  sort_order INT NOT NULL COMMENT '提交顺序，从 0 起',
  PRIMARY KEY (activity_id, material_id),
  CONSTRAINT fk_activity_courseware_activity FOREIGN KEY (activity_id) REFERENCES activity (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='活动课件；存素材关联 + name/url/type 快照；不拷贝 TOS';
