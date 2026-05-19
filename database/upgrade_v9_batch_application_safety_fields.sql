-- v9：批次申报安全责任字段与作废状态
-- 如果你已经执行过 v8 脚本，再执行本脚本。
-- 如果你还没有执行 v8，直接执行最新版 v8 即可，本脚本可以不执行。

ALTER TABLE batch_applications
    ADD COLUMN production_manager VARCHAR(50) NULL COMMENT '生产负责人',
    ADD COLUMN manager_phone VARCHAR(30) NULL COMMENT '负责人联系电话',
    ADD COLUMN production_address VARCHAR(255) NULL COMMENT '生产地址',
    ADD COLUMN expected_market_date DATE NULL COMMENT '预计上市日期',
    ADD COLUMN quality_commitment TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否确认质检与食品安全承诺';

ALTER TABLE batch_applications
    MODIFY COLUMN status VARCHAR(30) NOT NULL DEFAULT 'Pending'
        COMMENT '审批状态：Pending=待审核，Rejected=退回补正，Denied=不予通过，Converted=已转正式批次，Voided=已作废';
