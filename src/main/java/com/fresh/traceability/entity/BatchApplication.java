package com.fresh.traceability.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("batch_applications")
public class BatchApplication {
    @TableId(type = IdType.AUTO)
    private Integer applicationId;

    private String applicationNo;
    private String productName;
    private String origin;
    private LocalDate productionDate;
    private Integer shelfLifeDays;
    private Integer batchQuantity;
    private Integer manufacturerId;
    private String productionManager;
    private String managerPhone;
    private String productionAddress;
    private LocalDate expectedMarketDate;
    private Boolean qualityCommitment;
    private String description;
    private String imageUrl;
    private String status;
    private String reviewOpinion;
    private Integer reviewerId;
    private LocalDateTime reviewTime;
    private Integer convertedBatchId;
    private Integer createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String manufacturerName;

    @TableField(exist = false)
    private String reviewerName;

    @TableField(exist = false)
    private String convertedBatchCode;
}
