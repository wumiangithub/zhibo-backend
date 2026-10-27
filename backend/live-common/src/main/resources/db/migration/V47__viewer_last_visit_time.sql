-- D79：最近访问时间；可空。登录成功与 presence/ping 写入。

ALTER TABLE viewer
  ADD COLUMN last_visit_time DATETIME(3) NULL
    COMMENT '最近访问时间；登录与 presence/ping 写入'
    AFTER last_login_time;
