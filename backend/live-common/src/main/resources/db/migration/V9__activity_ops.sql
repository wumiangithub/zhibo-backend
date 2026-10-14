-- 第十迭代：上架 / 风控 / 售卖标签 / 软删，以及归属人、关联商品、审核记录
-- 已有活动回填为已上架、风控已通过、免费 / 长期有效，避免原来能开的场次被禁播

ALTER TABLE activity
  ADD COLUMN shelf_status VARCHAR(16) NOT NULL DEFAULT 'on'
    COMMENT '上架：pending 待上架 / on 已上架 / off 已下架 / banned 强制封禁' AFTER share_link,
  ADD COLUMN risk_status VARCHAR(16) NOT NULL DEFAULT 'passed'
    COMMENT '风控：pending 待审核 / passed 已通过 / rejected 已驳回' AFTER shelf_status,
  ADD COLUMN sell_type VARCHAR(16) NOT NULL DEFAULT 'free'
    COMMENT '售卖方式：free 免费 / paid 付费 / encrypted 加密 / specified 指定学员' AFTER risk_status,
  ADD COLUMN validity_label VARCHAR(32) NOT NULL DEFAULT '长期有效'
    COMMENT '有效期文案；本版固定「长期有效」' AFTER sell_type,
  ADD COLUMN deleted_at DATETIME(3) NULL
    COMMENT '软删时间；非空则列表与详情视为不存在' AFTER validity_label,
  ADD KEY idx_activity_shelf (shelf_status),
  ADD KEY idx_activity_deleted (deleted_at);

UPDATE activity
SET shelf_status = 'on',
    risk_status = 'passed',
    sell_type = 'free',
    validity_label = '长期有效';

CREATE TABLE activity_owner (
  activity_id VARCHAR(36) NOT NULL COMMENT '本站活动 UUID',
  user_id BIGINT NOT NULL COMMENT '员工 sys_useraccount.rs_mainkey_id',
  sort_order INT NOT NULL COMMENT '提交顺序，从 0 起',
  PRIMARY KEY (activity_id, user_id),
  CONSTRAINT fk_activity_owner_activity FOREIGN KEY (activity_id) REFERENCES activity (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='活动归属人；姓名按员工表实时查，不另存一份';

CREATE TABLE activity_product (
  activity_id VARCHAR(36) NOT NULL COMMENT '本站活动 UUID',
  spu_id VARCHAR(32) NOT NULL COMMENT '订单中心商品 spuId，十进制字符串',
  product_name VARCHAR(128) NOT NULL COMMENT '绑定时的商品名称快照',
  sort_order INT NOT NULL COMMENT '提交顺序，从 0 起',
  PRIMARY KEY (activity_id, spu_id),
  CONSTRAINT fk_activity_product_activity FOREIGN KEY (activity_id) REFERENCES activity (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='活动关联商品；只存快照，本站不代理商品列表';

CREATE TABLE activity_audit (
  id VARCHAR(36) NOT NULL PRIMARY KEY COMMENT '审核记录 UUID',
  activity_id VARCHAR(36) NOT NULL COMMENT '本站活动 UUID',
  result VARCHAR(16) NOT NULL COMMENT 'passed 通过 / rejected 驳回',
  reason VARCHAR(200) NULL COMMENT '驳回理由；通过为空',
  operator_id BIGINT NOT NULL COMMENT '审核人员工 id',
  operator_name VARCHAR(50) NOT NULL COMMENT '审核人姓名快照',
  created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '审核时间',
  KEY idx_activity_audit_activity_created (activity_id, created_at),
  CONSTRAINT fk_activity_audit_activity FOREIGN KEY (activity_id) REFERENCES activity (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='活动风控审核记录；可反复审';
