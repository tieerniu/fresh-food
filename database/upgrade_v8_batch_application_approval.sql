-- v8：批次申报审批
-- 目的：
-- 1. 合作基地先提交批次申报，不直接生成正式产品批次。
-- 2. 管理员审核通过后，系统再生成正式 product_batches 数据。
-- 3. 未审核申报不能进入溯源、二维码和消费者查询链路。

CREATE TABLE IF NOT EXISTS batch_applications (
    application_id INT PRIMARY KEY AUTO_INCREMENT COMMENT '批次申报ID',
    application_no VARCHAR(50) NOT NULL COMMENT '申报编号',
    product_name VARCHAR(100) NOT NULL COMMENT '产品名称',
    origin VARCHAR(100) NOT NULL COMMENT '产地',
    production_date DATE NOT NULL COMMENT '生产日期',
    shelf_life_days INT NOT NULL COMMENT '保质期天数',
    batch_quantity INT NOT NULL COMMENT '申报数量',
    manufacturer_id INT NOT NULL COMMENT '申报基地账号ID，对应 users.user_id',
    production_manager VARCHAR(50) NULL COMMENT '生产负责人',
    manager_phone VARCHAR(30) NULL COMMENT '负责人联系电话',
    production_address VARCHAR(255) NULL COMMENT '生产地址',
    expected_market_date DATE NULL COMMENT '预计上市日期',
    quality_commitment TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否确认质检与食品安全承诺',
    description TEXT NULL COMMENT '申报说明',
    image_url VARCHAR(500) NULL COMMENT '产品图片地址',
    status VARCHAR(30) NOT NULL DEFAULT 'Pending' COMMENT '审批状态：Pending=待审核，Rejected=退回补正，Denied=不予通过，Converted=已转正式批次，Voided=已作废',
    review_opinion VARCHAR(500) NULL COMMENT '审核意见',
    reviewer_id INT NULL COMMENT '审核人ID',
    review_time DATETIME NULL COMMENT '审核时间',
    converted_batch_id INT NULL COMMENT '转成正式批次后的批次ID',
    created_by INT NOT NULL COMMENT '创建人ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_batch_applications_no (application_no),
    KEY idx_batch_applications_status (status),
    KEY idx_batch_applications_manufacturer (manufacturer_id),
    KEY idx_batch_applications_converted_batch (converted_batch_id)
) COMMENT='批次申报审批表';
