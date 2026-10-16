-- 直播子系统角色-菜单关联（本站菜单，不写主系统 sys_uimsrole_menu）

CREATE TABLE live_role_menu (
  rs_mainkey_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  rs_status INT NOT NULL DEFAULT 1 COMMENT '1正常 -1已删除',
  role_id BIGINT NOT NULL COMMENT 'sys_uimsrole.rs_mainkey_id，与主系统共用角色',
  menu_code VARCHAR(64) NOT NULL COMMENT '本站菜单编码',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  updated_by BIGINT NULL COMMENT '操作人 sys_useraccount.rs_mainkey_id',
  PRIMARY KEY (rs_mainkey_id),
  UNIQUE KEY uk_live_role_menu (role_id, menu_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='直播子系统角色菜单关联';
