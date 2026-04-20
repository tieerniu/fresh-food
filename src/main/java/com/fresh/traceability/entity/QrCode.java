package com.fresh.traceability.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("qr_codes")
public class QrCode {
    @TableId(type = IdType.AUTO)
    private Integer qrId;

    private String uniqueCode;
    private Integer batchId;
    private String securityToken;
    private String status; // Active, Recalled, Expired（旧数据中的 Sold 会归一化为 Active）
    private Integer scanCount;
    private LocalDateTime createdAt;
    private LocalDateTime firstScanTime;
}
