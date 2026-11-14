-- activity_order.order_status：由本站 1～5 改为原样存 OMS 码（1010+）。
-- TINYINT 装不下 1010，改为 SMALLINT；存量 1～5 按旧映射回写。

ALTER TABLE `activity_order`
  MODIFY COLUMN `order_status` smallint NOT NULL DEFAULT 1030
    COMMENT 'OMS 订单状态：1010待支付 1020部分支付 1030已支付 1040撞单 1050已签约 1060已取消 1070分期退款中 1071退款中 1080部分退款 1081已退款';

UPDATE `activity_order`
SET `order_status` = CASE `order_status`
  WHEN 1 THEN 1010
  WHEN 2 THEN 1030
  WHEN 3 THEN 1071
  WHEN 4 THEN 1080
  WHEN 5 THEN 1081
  ELSE `order_status`
END
WHERE `order_status` BETWEEN 1 AND 5;
