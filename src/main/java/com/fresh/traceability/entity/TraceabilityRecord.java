package com.fresh.traceability.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 溯源记录实体类
 */
@Data
@TableName("traceability_records")
public class TraceabilityRecord {
    @TableId(type = IdType.AUTO)
    private Integer recordId;

    private Integer batchId;
    private Integer operatorId;
    private String nodeStage; // 生产, 加工, 质检, 物流, 销售
    private String location;
    private String operationDetail;
    private String operator; // 操作员姓名（新增）
    private BigDecimal temperatureData;
    private LocalDateTime recordedAt;
}
