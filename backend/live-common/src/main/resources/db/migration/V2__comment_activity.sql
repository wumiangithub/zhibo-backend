-- 仅本站库 zhibo.activity 字段说明（不动其他库）
ALTER TABLE activity COMMENT = '本站直播活动（主键为本站 UUID，云侧房间号在 provider_room_id）';

ALTER TABLE activity
  MODIFY COLUMN id VARCHAR(36) NOT NULL
    COMMENT '本站活动主键 UUID，对外 API / 路由使用，不是微吼 webinar_id',
  MODIFY COLUMN provider VARCHAR(32) NOT NULL
    COMMENT '直播服务商：vhall 微吼 / aliyun 阿里云 / tencent 腾讯云；创建后不可改',
  MODIFY COLUMN provider_room_id VARCHAR(64) NULL
    COMMENT '云侧房间/活动号；微吼时即 webinar_id（详情页「外部房间号」）',
  MODIFY COLUMN title VARCHAR(64) NOT NULL
    COMMENT '直播名称；映射微吼 subject',
  MODIFY COLUMN introduction VARCHAR(256) NULL
    COMMENT '直播简介；有值时可映射微吼 introduction',
  MODIFY COLUMN start_time VARCHAR(32) NOT NULL
    COMMENT '开始时间，格式 yyyy-MM-dd HH:mm:ss；发给微吼裁到分钟',
  MODIFY COLUMN end_time VARCHAR(32) NULL
    COMMENT '结束时间，格式 yyyy-MM-dd HH:mm:ss；只落本站，不发微吼 create',
  MODIFY COLUMN type INT NOT NULL
    COMMENT '直播类型：1 音频 / 2 视频 / 3 互动 / 4 录播；创建后不可改',
  MODIFY COLUMN state INT NOT NULL DEFAULT 2
    COMMENT '活动状态：1 直播 / 2 预告 / 3 结束 / 4 点播 / 5 回放',
  MODIFY COLUMN viewer_style VARCHAR(32) NULL
    COMMENT '观看端样式：traditional 传统直播间 / immersive 沉浸直播间；本站配置',
  MODIFY COLUMN ultra_low_latency TINYINT(1) NULL
    COMMENT '是否超低延迟；true 时创建微吼传 no_delay_webinar=1',
  MODIFY COLUMN virtual_avatar TINYINT(1) NULL
    COMMENT '是否虚拟形象；本站配置，当前不转发微吼',
  MODIFY COLUMN cover_url VARCHAR(1024) NULL
    COMMENT '详情封面图 URL（一般为微吼上传返回的 domain_url）',
  MODIFY COLUMN warmup_type VARCHAR(16) NULL
    COMMENT '暖场类型：image 图 / video 视频',
  MODIFY COLUMN warmup_url VARCHAR(1024) NULL
    COMMENT '暖场素材 URL',
  MODIFY COLUMN promo_cover_url VARCHAR(1024) NULL
    COMMENT '宣传封面图 URL',
  MODIFY COLUMN detail_html MEDIUMTEXT NULL
    COMMENT '直播详情富文本 HTML',
  MODIFY COLUMN share_link VARCHAR(1024) NULL
    COMMENT '云侧分享/观看链接（如微吼 share_link），运营复制用；本站观看用 /watch/{本站id}',
  MODIFY COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
    COMMENT '本站创建时间',
  MODIFY COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
    COMMENT '本站最后更新时间';
