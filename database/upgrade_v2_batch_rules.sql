-- 生鲜防伪溯源系统数据库升级脚本
-- 作用：
-- 1. 给 product_batches 增加批次数量字段 batch_quantity
-- 2. 初始化已有批次的 batch_quantity，保证不小于已生成二维码数量
-- 3. 给 batch_code 增加唯一索引

-- 第一步：增加批次数量字段（如果你已经手动加过，就跳过这一句）
ALTER TABLE product_batches
    ADD COLUMN batch_quantity INT NULL COMMENT '批次数量';

-- 第二步：用已有二维码数量回填旧数据，至少保证为 1
UPDATE product_batches p
SET p.batch_quantity = GREATEST(
    COALESCE(
        (SELECT COUNT(*) FROM qr_codes q WHERE q.batch_id = p.batch_id),
        0
    ),
    1
)
WHERE p.batch_quantity IS NULL OR p.batch_quantity <= 0;

-- 第三步：将字段改为非空
ALTER TABLE product_batches
    MODIFY COLUMN batch_quantity INT NOT NULL COMMENT '批次数量';

-- 第四步：如果以下查询返回 0 行，再执行唯一索引语句
-- SELECT batch_code, COUNT(*) AS cnt
-- FROM product_batches
-- GROUP BY batch_code
-- HAVING COUNT(*) > 1;

CREATE UNIQUE INDEX uk_product_batches_batch_code
    ON product_batches(batch_code);
