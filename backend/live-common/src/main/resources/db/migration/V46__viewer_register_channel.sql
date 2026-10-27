-- D78：首次注册渠道。1 微信 / 2 导入。只写首次；存量默认 1。

ALTER TABLE viewer
  ADD COLUMN register_channel INT NOT NULL DEFAULT 1
    COMMENT '首次注册渠道：1 微信 / 2 导入；只写首次'
    AFTER unionid;
