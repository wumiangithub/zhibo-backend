-- Flyway 自建表：记录「哪些 V* 迁移脚本已经跑过」。不是业务数据，一般不要手改、不要删。
-- 类比：像前端的 lockfile / 版本清单，用来避免重复建表、也避免漏跑脚本。

ALTER TABLE flyway_schema_history
  COMMENT = 'Flyway 迁移记账本。每成功（或失败）执行一条 db/migration 脚本就记一行。业务表结构以 V1～Vn 脚本为准，本表只记执行历史';

ALTER TABLE flyway_schema_history
  MODIFY COLUMN installed_rank INT NOT NULL
    COMMENT '执行次序（主键）。数字越小越早跑；同一库里按这个顺序追溯迁移历史',
  MODIFY COLUMN version VARCHAR(50) NULL
    COMMENT '脚本版本号。对应文件名 V1、V2… 里的数字。基线/重复类操作可能为空',
  MODIFY COLUMN description VARCHAR(200) NOT NULL
    COMMENT '脚本说明。通常来自文件名双下划线后的英文，如 create_activity',
  MODIFY COLUMN type VARCHAR(20) NOT NULL
    COMMENT '迁移类型。常见 SQL=普通版本脚本；还有 BASELINE/UNDO 等 Flyway 内部类型',
  MODIFY COLUMN script VARCHAR(1000) NOT NULL
    COMMENT '脚本文件名，如 V6__presence_event_and_provider_room_uk.sql',
  MODIFY COLUMN checksum INT NULL
    COMMENT '脚本内容校验和。已执行过的脚本若再被改动，启动时校验失败，防止库和代码对不上',
  MODIFY COLUMN installed_by VARCHAR(100) NOT NULL
    COMMENT '执行时用的数据库用户名（如 root）',
  MODIFY COLUMN installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
    COMMENT '这条迁移实际执行的时间',
  MODIFY COLUMN execution_time INT NOT NULL
    COMMENT '执行耗时（毫秒）',
  MODIFY COLUMN success TINYINT(1) NOT NULL
    COMMENT '1=成功；0=失败。失败行存在时 Flyway 会拒绝继续迁，需先修好再启动';
