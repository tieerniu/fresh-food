package com.fresh.traceability.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("product_batches")
public class ProductBatch {
    @TableId(type = IdType.AUTO)
    private Integer batchId;

    private String batchCode;
    private String productName;
    private String origin;
    private LocalDate productionDate;
    private Integer shelfLifeDays;
    private Integer batchQuantity;
    private Integer manufacturerId;
    private String description;
    private String imageUrl;
    private LocalDateTime createdAt;

    /** 供应商名称（非数据库字段，由关联查询回填） */
    @TableField(exist = false)
    private String manufacturerName;

    /** 已生成二维码数量（非数据库字段，由关联查询回填） */
    @TableField(exist = false)
    private Integer generatedQrCount;
}
