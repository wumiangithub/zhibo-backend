-- 观众表同步用户中心（钱坤云）uid / username，供 JWT 与订单中心跨系统标识（D116）
ALTER TABLE viewer
    ADD COLUMN uid BIGINT NULL COMMENT '同步自用户中心 LoginVo.uid；订单中心 third_user_id；本站 JWT claim' AFTER unionid,
    ADD COLUMN username VARCHAR(64) NULL COMMENT '同步自用户中心 LoginVo.username；订单中心 username；本站 JWT claim' AFTER uid;

CREATE UNIQUE INDEX uk_viewer_uid ON viewer (uid);
