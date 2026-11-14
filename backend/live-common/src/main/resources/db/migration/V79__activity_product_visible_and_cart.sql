-- 中控台商品：单品显示/隐藏 + 小黄车总开关
ALTER TABLE activity_product
  ADD COLUMN visible TINYINT(1) NOT NULL DEFAULT 1
    COMMENT '直播间是否展示该绑定商品；1显示 0隐藏'
    AFTER sort_order;

ALTER TABLE activity
  ADD COLUMN cart_enabled TINYINT(1) NOT NULL DEFAULT 1
    COMMENT '小黄车总开关；0时观看端商品列表为空'
    AFTER playback_fast_forward_allow;
