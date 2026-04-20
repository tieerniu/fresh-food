-- 生鲜防伪溯源系统数据库升级脚本
-- 作用：
-- 1. 给 scan_logs 增加设备标识、客户端信息、扫码来源字段
-- 2. 为移动端 H5 的多设备风控提供数据基础
-- 3. 补充常用查询索引，优化短时间窗口风控统计

ALTER TABLE scan_logs
    ADD COLUMN device_id VARCHAR(64) NULL COMMENT 'H5设备标识';

ALTER TABLE scan_logs
    ADD COLUMN user_agent VARCHAR(255) NULL COMMENT '客户端 User-Agent';

ALTER TABLE scan_logs
    ADD COLUMN scan_source VARCHAR(30) NULL COMMENT '扫码来源：H5 / Manual / Legacy';

UPDATE scan_logs
SET scan_source = 'Legacy'
WHERE scan_source IS NULL OR scan_source = '';

ALTER TABLE scan_logs
    MODIFY COLUMN scan_source VARCHAR(30) NOT NULL DEFAULT 'Legacy' COMMENT '扫码来源：H5 / Manual / Legacy';

CREATE INDEX idx_scan_logs_unique_code_time
    ON scan_logs(unique_code, scan_time);

CREATE INDEX idx_scan_logs_device_id
    ON scan_logs(device_id);
