CREATE DATABASE IF NOT EXISTS fresh_food_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE fresh_food_db;

CREATE TABLE IF NOT EXISTS users (
    user_id INT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL,
    full_name VARCHAR(100) NULL,
    contact_info VARCHAR(100) NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_users_username (username),
    KEY idx_users_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS suppliers (
    supplier_id INT PRIMARY KEY AUTO_INCREMENT,
    supplier_name VARCHAR(100) NOT NULL,
    contact_person VARCHAR(50) NULL,
    contact_phone VARCHAR(30) NULL,
    address VARCHAR(255) NULL,
    qualification_level VARCHAR(50) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_suppliers_name (supplier_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS product_batches (
    batch_id INT PRIMARY KEY AUTO_INCREMENT,
    batch_code VARCHAR(50) NOT NULL,
    product_name VARCHAR(100) NOT NULL,
    origin VARCHAR(100) NULL,
    production_date DATE NULL,
    shelf_life_days INT NULL,
    batch_quantity INT NOT NULL DEFAULT 1,
    manufacturer_id INT NULL,
    description TEXT NULL,
    image_url VARCHAR(500) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_product_batches_batch_code (batch_code),
    KEY idx_product_batches_manufacturer (manufacturer_id),
    KEY idx_product_batches_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS qr_codes (
    qr_id INT PRIMARY KEY AUTO_INCREMENT,
    unique_code VARCHAR(100) NOT NULL,
    batch_id INT NOT NULL,
    security_token VARCHAR(100) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'Active',
    scan_count INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    first_scan_time DATETIME NULL,
    UNIQUE KEY uk_qr_codes_unique_code (unique_code),
    KEY idx_qr_codes_batch_id (batch_id),
    KEY idx_qr_codes_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS traceability_records (
    record_id INT PRIMARY KEY AUTO_INCREMENT,
    batch_id INT NOT NULL,
    operator_id INT NULL,
    node_stage VARCHAR(50) NOT NULL,
    location VARCHAR(255) NULL,
    operation_detail TEXT NULL,
    operator VARCHAR(100) NULL,
    temperature_data DECIMAL(10,2) NULL,
    recorded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_traceability_records_batch (batch_id),
    KEY idx_traceability_records_recorded_at (recorded_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS quality_inspections (
    inspection_id INT PRIMARY KEY AUTO_INCREMENT,
    batch_id INT NOT NULL,
    source_type VARCHAR(20) NOT NULL DEFAULT 'Routine',
    source_warning_id INT NULL,
    inspection_status VARCHAR(20) NOT NULL DEFAULT 'Completed',
    inspector VARCHAR(50) NULL,
    inspection_org VARCHAR(100) NULL,
    report_no VARCHAR(100) NULL,
    report_url VARCHAR(255) NULL,
    inspection_result VARCHAR(50) NULL,
    is_recalled TINYINT(1) NOT NULL DEFAULT 0,
    remarks TEXT NULL,
    inspection_date DATE NULL,
    KEY idx_quality_inspections_batch (batch_id),
    KEY idx_quality_inspections_source_warning_id (source_warning_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS security_warnings (
    warning_id INT PRIMARY KEY AUTO_INCREMENT,
    unique_code VARCHAR(100) NOT NULL,
    warning_type VARCHAR(50) NOT NULL,
    warning_content TEXT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'Pending',
    disposal_type VARCHAR(40) NULL,
    linked_inspection_id INT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handled_at DATETIME NULL,
    KEY idx_security_warnings_unique_code (unique_code),
    KEY idx_security_warnings_status (status),
    KEY idx_security_warnings_linked_inspection_id (linked_inspection_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS scan_logs (
    id INT PRIMARY KEY AUTO_INCREMENT,
    qr_code_id INT NULL,
    unique_code VARCHAR(100) NULL,
    scan_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(64) NULL,
    device_id VARCHAR(64) NULL,
    user_agent VARCHAR(255) NULL,
    scan_source VARCHAR(30) NOT NULL DEFAULT 'Legacy',
    KEY idx_scan_logs_qr_code_id (qr_code_id),
    KEY idx_scan_logs_unique_code_time (unique_code, scan_time),
    KEY idx_scan_logs_device_id (device_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

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
    status VARCHAR(30) NOT NULL DEFAULT 'Pending' COMMENT '审批状态',
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='批次申报审批表';

INSERT INTO users (username, password_hash, role, full_name, contact_info, enabled)
SELECT 'admin', 'b7f88785dfd3a500a6b3011449a1628e', 'admin', '系统管理员', '', 1
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'admin');
