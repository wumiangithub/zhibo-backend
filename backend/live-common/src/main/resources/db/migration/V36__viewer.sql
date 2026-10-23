-- D73：观众本人与渠道登录凭证分开。openid 不放在 viewer 上。
-- channel：1 微信小程序 / 2 微信公众号 / 3 手机网页 / 4 App。本轮只写入 1。
-- 不存 session_key，不建 refresh 表。

CREATE TABLE viewer (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '观众主键',
  phone VARCHAR(20) NULL COMMENT '纯数字手机号，不含国家码；接口不回明文',
  phone_country_code VARCHAR(8) NULL COMMENT '手机号国家码，如 86',
  nickname VARCHAR(64) NULL COMMENT '昵称；本轮登录不写',
  avatar_url VARCHAR(512) NULL COMMENT '头像 URL；本轮登录不写',
  status INT NOT NULL DEFAULT 1 COMMENT '1 正常 / 0 停用',
  agreed_time DATETIME(3) NOT NULL COMMENT '最近一次勾选协议并登录成功的时间',
  last_login_time DATETIME(3) NOT NULL COMMENT '最近一次登录成功时间',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_viewer_phone (phone)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='观众本人；与员工账号无关';

CREATE TABLE viewer_identity (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '渠道凭证主键',
  viewer_id BIGINT NOT NULL COMMENT '观众 viewer.id',
  channel INT NOT NULL COMMENT '1 微信小程序 / 2 微信公众号 / 3 手机网页 / 4 App',
  openid VARCHAR(64) NULL COMMENT '该渠道 openid；不存 session_key',
  unionid VARCHAR(64) NULL COMMENT '微信 unionid；以后公众号按它对同一个观众',
  created_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  updated_time DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_viewer_identity_channel_openid (channel, openid),
  KEY idx_viewer_identity_unionid (unionid),
  KEY idx_viewer_identity_viewer (viewer_id),
  CONSTRAINT fk_viewer_identity_viewer FOREIGN KEY (viewer_id) REFERENCES viewer (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='观众渠道登录凭证；本轮只写入 channel=1';
