package com.fresh.traceability.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 防伪预警实体类
 */
@Data
@TableName("security_warnings")
public class SecurityWarning {
    @TableId(type = IdType.AUTO)
    private Integer warningId;

    private String uniqueCode;
    private String warningType;
    private String warningContent;
    private String status;          // Pending, Resolved, Ignored
    private String disposalType;
    private Integer linkedInspectionId;
    private LocalDateTime createdAt;
    private LocalDateTime handledAt;

    @TableField(exist = false)
    private Integer qrId;

    @TableField(exist = false)
    private Integer batchId;

    @TableField(exist = false)
    private String batchCode;

    @TableField(exist = false)
    private String productName;

    @TableField(exist = false)
    private String manufacturerName;

    @TableField(exist = false)
    private String qrStatus;

    @TableField(exist = false)
    private Integer scanCount;

    @TableField(exist = false)
    private String linkedInspectionStatus;

    @TableField(exist = false)
    private String linkedInspectionResult;
}
