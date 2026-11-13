-- 小黄车商品点击流水：直播数据「直播带货」点击次数/人数
CREATE TABLE IF NOT EXISTS `activity_product_click` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `activity_id` varchar(32) NOT NULL COMMENT '本站活动 UUID，对齐 activity.id',
  `spu_id` varchar(32) NOT NULL COMMENT '订单中心商品 spuId，十进制字符串',
  `username` varchar(64) NOT NULL COMMENT '观众 username（JWT sub）',
  `clicked_at` datetime(3) NOT NULL COMMENT '点击时间',
  PRIMARY KEY (`id`),
  KEY `idx_activity_spu` (`activity_id`, `spu_id`),
  KEY `idx_activity_spu_username` (`activity_id`, `spu_id`, `username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='小黄车商品点击流水';
