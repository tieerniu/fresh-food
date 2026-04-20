package com.fresh.traceability.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 合作基地/供应商实体类
 */
@Data
@TableName("suppliers")
public class Supplier {
    @TableId(type = IdType.AUTO)
    private Integer supplierId;

    private String supplierName;
    private String contactPerson;
    private String contactPhone;
    private String address;
    private String qualificationLevel;
    private LocalDateTime createdAt;

    @TableField(exist = false)
    private Integer accountUserId;

    @TableField(exist = false)
    private String accountUsername;

    @TableField(exist = false)
    private Boolean accountEnabled;

    @TableField(exist = false)
    private Integer relatedBatchCount;
}
