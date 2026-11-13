-- 对齐已发布订单回调合同（2026-11-12《课程列表_订单回调接口文档》）。
-- V72 已执行，本脚本只做增量变更，不改写 V72。

ALTER TABLE `activity_order`
  DROP INDEX `idx_customer_id`,
  DROP INDEX `idx_course_id`,
  DROP COLUMN `oms_order_id`,
  DROP COLUMN `course_id`,
  CHANGE COLUMN `customer_id` `username` varchar(64) NOT NULL COMMENT '下单人用户名，对齐 viewer.username / 订单中心 third_user_id（D118）',
  CHANGE COLUMN `salesman_id` `salesman_id` varchar(64) DEFAULT NULL COMMENT '对方 OMS 业务员 ID（字符串）',
  ADD COLUMN `product_type` varchar(50) DEFAULT NULL COMMENT '商品类型' AFTER `product_name`,
  ADD COLUMN `channel` int DEFAULT NULL COMMENT '订单渠道' AFTER `salesman_id`,
  ADD KEY `idx_username` (`username`);

CREATE TABLE IF NOT EXISTS `activity_order_course` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id` bigint NOT NULL COMMENT '本站 activity_order.id',
  `course_id` varchar(32) NOT NULL COMMENT '本站课程 id，对齐 course.id / 对外 materialId',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_course` (`order_id`, `course_id`),
  KEY `idx_course_id` (`course_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单关联课程，一单多课';
