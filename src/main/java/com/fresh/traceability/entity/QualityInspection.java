package com.fresh.traceability.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 质检记录实体类
 */
@Data
@TableName("quality_inspections")
public class QualityInspection {
    @TableId(type = IdType.AUTO)
    private Integer inspectionId;

    private Integer batchId;
    private String sourceType;
    private Integer sourceWarningId;
    private String inspectionStatus;
    private String inspector;
    private String inspectionOrg;
    private String reportNo;
    private String reportUrl;
    private String inspectionResult;   // 合格 / 不合格
    private Boolean isRecalled;
    private String remarks;
    private LocalDate inspectionDate;
}
