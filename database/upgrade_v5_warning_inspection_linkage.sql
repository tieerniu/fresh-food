-- 生鲜防伪溯源系统数据库升级脚本
-- 作用：
-- 1. 给质检表增加“来源 / 任务状态 / 报告信息”字段
-- 2. 给预警表增加“处置类型 / 关联质检任务”字段
-- 3. 为历史数据回填默认值，形成预警 -> 质检 -> 召回闭环基础

ALTER TABLE quality_inspections
    ADD COLUMN source_type VARCHAR(20) NULL COMMENT '来源类型：Routine / Warning';

ALTER TABLE quality_inspections
    ADD COLUMN source_warning_id INT NULL COMMENT '来源预警ID';

ALTER TABLE quality_inspections
    ADD COLUMN inspection_status VARCHAR(20) NULL COMMENT '任务状态：Pending / Completed';

ALTER TABLE quality_inspections
    ADD COLUMN inspection_org VARCHAR(100) NULL COMMENT '质检机构';

ALTER TABLE quality_inspections
    ADD COLUMN report_no VARCHAR(100) NULL COMMENT '报告编号';

ALTER TABLE quality_inspections
    ADD COLUMN report_url VARCHAR(255) NULL COMMENT '报告附件链接';

UPDATE quality_inspections
SET source_type = 'Routine'
WHERE source_type IS NULL OR source_type = '';

UPDATE quality_inspections
SET inspection_status = CASE
    WHEN inspection_result IS NULL OR inspection_result = '' THEN 'Pending'
    ELSE 'Completed'
END
WHERE inspection_status IS NULL OR inspection_status = '';

ALTER TABLE quality_inspections
    MODIFY COLUMN source_type VARCHAR(20) NOT NULL DEFAULT 'Routine' COMMENT '来源类型：Routine / Warning';

ALTER TABLE quality_inspections
    MODIFY COLUMN inspection_status VARCHAR(20) NOT NULL DEFAULT 'Completed' COMMENT '任务状态：Pending / Completed';

ALTER TABLE security_warnings
    ADD COLUMN disposal_type VARCHAR(40) NULL COMMENT '处置类型：Ignore / InspectionCreated / VerifiedPass / EscalatedRecall / Frozen';

ALTER TABLE security_warnings
    ADD COLUMN linked_inspection_id INT NULL COMMENT '关联质检任务ID';

UPDATE security_warnings w
LEFT JOIN qr_codes q ON q.unique_code = w.unique_code
SET w.disposal_type = CASE
    WHEN w.status IN ('Ignored', '已忽略') THEN 'Ignore'
    WHEN w.status IN ('Resolved', '已处理') AND q.status = 'Recalled' THEN 'Frozen'
    WHEN w.status IN ('Resolved', '已处理') THEN 'InspectionCompleted'
    ELSE w.disposal_type
END
WHERE w.disposal_type IS NULL OR w.disposal_type = '';

CREATE INDEX idx_quality_inspections_source_warning_id
    ON quality_inspections(source_warning_id);

CREATE INDEX idx_security_warnings_linked_inspection_id
    ON security_warnings(linked_inspection_id);
