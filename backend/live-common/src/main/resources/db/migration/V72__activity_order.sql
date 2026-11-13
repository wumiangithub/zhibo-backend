-- 本站订单表 activity_order：订单中心回调写入，支撑列表/导出、按直播间/课程统计、工作台经营数据
-- 口径与字段见 zhibo-shared/docs/订单落库.md

CREATE TABLE IF NOT EXISTS `activity_order` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_no` varchar(64) NOT NULL COMMENT '对方 OMS 订单号（回调幂等键，列表筛选/展示）',
  `oms_order_id` bigint DEFAULT NULL COMMENT '对方 OMS 订单 id（回调另带数字 id 时才落）',
  `customer_id` varchar(64) NOT NULL COMMENT '对方 OMS 客户 id（列表「用户ID」）',
  `user_nickname` varchar(50) DEFAULT NULL COMMENT '用户昵称（列表展示/筛选，回调或下单时带回）',
  `product_id` bigint DEFAULT NULL COMMENT '对方 OMS 商品 id',
  `product_name` varchar(200) DEFAULT NULL COMMENT '商品名称（列表展示/筛选，回调或下单时带回）',
  `course_id` bigint DEFAULT NULL COMMENT '对方 OMS 课程 id（按课程统计；不映射成本站 course.id）',
  `activity_id` varchar(32) DEFAULT NULL COMMENT '本站直播活动 id，对齐 activity.id（按直播间统计；下单或回调必须带回）',
  `salesman_id` bigint DEFAULT NULL COMMENT '对方 OMS 业务员 id（列表若按「本人名下」过滤用）',
  `order_time` datetime DEFAULT NULL COMMENT '下单时间',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间（待付款可为空）',
  `pay_amount` decimal(12,2) DEFAULT NULL COMMENT '支付金额（元），待付款可为空；经营数据今日/累计支付金额用',
  `order_status` tinyint NOT NULL DEFAULT 2 COMMENT '订单状态：1待付款 2已付款 3退款中 4部分退款 5全部退款',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '本站写入时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '本站最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_activity_id` (`activity_id`),
  KEY `idx_course_id` (`course_id`),
  KEY `idx_order_status` (`order_status`),
  KEY `idx_order_time` (`order_time`),
  KEY `idx_pay_time` (`pay_time`),
  KEY `idx_customer_id` (`customer_id`),
  KEY `idx_salesman_id` (`salesman_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='本站订单表，订单中心回调写入，支撑列表/导出及按直播间、课程统计';
