-- 生鲜防伪溯源系统数据库升级脚本
-- 作用：
-- 1. 给 users 表增加 enabled 字段
-- 2. 为历史账号回填默认启用状态

ALTER TABLE users
    ADD COLUMN enabled TINYINT(1) NULL DEFAULT 1 COMMENT '账号是否启用，1=启用，0=禁用';

UPDATE users
SET enabled = 1
WHERE enabled IS NULL;

ALTER TABLE users
    MODIFY COLUMN enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '账号是否启用，1=启用，0=禁用';
