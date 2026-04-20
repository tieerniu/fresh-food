package com.fresh.traceability.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 扫码日志实体类
 */
@Data
@TableName("scan_logs")
public class ScanLog {
    @TableId(type = IdType.AUTO)
    private Integer id;

    private Integer qrCodeId;
    private String uniqueCode;
    private LocalDateTime scanTime;
    private String ipAddress;
    private String deviceId;
    private String userAgent;
    private String scanSource;
}
