package com.fresh.traceability.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fresh.traceability.entity.ProductBatch;
import com.fresh.traceability.entity.QrCode;
import com.fresh.traceability.entity.ScanLog;
import com.fresh.traceability.entity.TraceabilityRecord;
import com.fresh.traceability.mapper.ProductBatchMapper;
import com.fresh.traceability.mapper.QrCodeMapper;
import com.fresh.traceability.mapper.ScanLogMapper;
import com.fresh.traceability.mapper.TraceabilityRecordMapper;
import com.fresh.traceability.service.WarningService;
import com.fresh.traceability.util.QrCodeUtil;
import com.fresh.traceability.util.QrCodeStatusHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;


import javax.servlet.http.HttpServletRequest;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 溯源控制器
 * 提供溯源记录的增删改查接口
 */
@RestController
@RequestMapping("/api/trace")
public class TraceabilityController {

    @Autowired
    private TraceabilityRecordMapper recordMapper;

    @Autowired
    private QrCodeMapper qrCodeMapper;

    @Autowired
    private ProductBatchMapper productBatchMapper;

    @Autowired
    private ScanLogMapper scanLogMapper;

    @Autowired
    private WarningService warningService;

    @Value("${app.h5-base-url:}")
    private String h5BaseUrl;

    /**
     * 接口1：上传溯源信息
     * POST /api/trace/add
     *
     * @param record 溯源记录对象（JSON 格式）
     * @return 操作结果
     */
    @PostMapping("/add")
    public Map<String, Object> addTraceRecord(@RequestBody TraceabilityRecord record, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();

        Integer currentUserId = (Integer) request.getAttribute("currentUserId");
        String currentRole = (String) request.getAttribute("currentUserRole");
        if (!canManageTrace(currentRole)) {
            result.put("success", false);
            result.put("message", "仅管理员或企业用户可操作");
            return result;
        }

        // 参数校验
        if (record.getBatchId() == null) {
            result.put("success", false);
            result.put("message", "缺少必填字段：batchId");
            return result;
        }

        ProductBatch batch = productBatchMapper.selectById(record.getBatchId());
        if (batch == null) {
            result.put("success", false);
            result.put("message", "批次不存在");
            return result;
        }

        if ("enterprise".equals(currentRole) && !Objects.equals(batch.getManufacturerId(), currentUserId)) {
            result.put("success", false);
            result.put("message", "无权给其他企业批次新增溯源信息");
            return result;
        }

        // 设置记录时间
        if (record.getRecordedAt() == null) {
            record.setRecordedAt(LocalDateTime.now());
        }
        record.setOperatorId(currentUserId);

        // 插入数据库
        int rows = recordMapper.insert(record);

        if (rows > 0) {
            result.put("success", true);
            result.put("message", "溯源记录添加成功");
            result.put("recordId", record.getRecordId());
        } else {
            result.put("success", false);
            result.put("message", "添加失败");
        }

        return result;
    }

    /**
     * 接口2：扫码查询溯源详情
     * GET /api/trace/query/{uniqueCode}
     *
     * @param uniqueCode 二维码唯一标识
     * @return 产品信息 + 溯源记录列表
     */
    @GetMapping("/query/{uniqueCode}")
    public Map<String, Object> queryTraceability(
            @PathVariable String uniqueCode,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();

        // 1. 根据唯一码查询二维码信息
        QrCode qrCode = qrCodeMapper.selectByUniqueCode(uniqueCode);

        if (qrCode == null) {
            result.put("success", false);
            result.put("message", "无效的二维码");
            return result;
        }
        normalizeLegacyStatus(qrCode);

        // 2. 查询产品批次信息
        ProductBatch batch = productBatchMapper.selectById(qrCode.getBatchId());
        if (batch == null) {
            result.put("success", false);
            result.put("message", "产品信息不存在");
            return result;
        }

        // 2.5 自动判断过期逻辑
        if (batch.getProductionDate() != null && batch.getShelfLifeDays() != null) {
            LocalDate productionDate = batch.getProductionDate();
            int shelfLifeDays = batch.getShelfLifeDays();
            LocalDate expirationDate = productionDate.plusDays(shelfLifeDays);
            LocalDate today = LocalDate.now();

            if (today.isAfter(expirationDate)) {
                // 极简物理状态机：只处理生命周期，召回状态不再自动切换为过期
                qrCodeMapper.markExpiredIfEligible(qrCode.getQrId());
                qrCode = qrCodeMapper.selectByUniqueCode(uniqueCode);
                normalizeLegacyStatus(qrCode);
            }
        }

        // 3. 查询所有溯源节点记录（按时间正序排列）
        QueryWrapper<TraceabilityRecord> recordQuery = new QueryWrapper<>();
        recordQuery.eq("batch_id", qrCode.getBatchId());
        recordQuery.orderByAsc("recorded_at");
        List<TraceabilityRecord> records = recordMapper.selectList(recordQuery);

        // 4. 更新扫码次数
        qrCodeMapper.increaseScanCount(uniqueCode);
        qrCode = qrCodeMapper.selectByUniqueCode(uniqueCode);
        normalizeLegacyStatus(qrCode);
        int scanCount = qrCode.getScanCount() == null ? 0 : qrCode.getScanCount();

        // 5. 记录扫码日志（用于统计与风控）
        try {
            ScanLog scanLog = new ScanLog();
            scanLog.setQrCodeId(qrCode.getQrId());
            scanLog.setUniqueCode(uniqueCode);
            scanLog.setScanTime(LocalDateTime.now());
            scanLog.setIpAddress(getClientIp(request));
            scanLog.setDeviceId(getDeviceId(request));
            scanLog.setUserAgent(getUserAgent(request));
            scanLog.setScanSource(getScanSource(request));
            scanLogMapper.insert(scanLog);
        } catch (Exception e) {
            // 日志记录失败不影响主流程
            System.err.println("记录扫码日志失败: " + e.getMessage());
        }

        WarningService.ScanRiskAssessment riskAssessment = WarningService.ScanRiskAssessment.normal();
        try {
            riskAssessment = warningService.assessScanRisk(qrCode);
            warningService.createFrequentScanWarningIfNeeded(qrCode, riskAssessment);
        } catch (Exception e) {
            System.err.println("风控分析或预警写入失败: " + e.getMessage());
        }

        // 6. 组装返回结果
        result.put("success", true);
        result.put("product", batch);
        result.put("qrCodeStatus", qrCode.getStatus());
        result.put("scanCount", scanCount);
        result.put("riskLevel", riskAssessment.getLevel());
        result.put("riskMessage", riskAssessment.getMessage());
        result.put("traceRecords", records);
        result.put("message", "查询成功");

        return result;
    }

    @GetMapping("/public-config")
    public Map<String, Object> getPublicConfig(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("h5BaseUrl", resolveH5BaseUrl(request));
        return result;
    }

    /**
     * 获取客户端 IP 地址
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    private String getDeviceId(HttpServletRequest request) {
        String deviceId = request.getHeader("X-Trace-Device-Id");
        return deviceId == null ? "" : deviceId.trim();
    }

    private String getUserAgent(HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return userAgent == null ? "" : userAgent;
    }

    private String getScanSource(HttpServletRequest request) {
        String scanSource = request.getHeader("X-Scan-Source");
        if (scanSource == null || scanSource.trim().isEmpty()) {
            return "H5";
        }
        return scanSource.trim();
    }

    /**
     * 接口3：生成二维码（测试用）
     * GET /api/trace/generate_qr?uniqueCode=xxx
     *
     * @param uniqueCode 二维码唯一标识
     * @return Base64 格式的二维码图片
     */
    @GetMapping("/generate_qr")
    public Map<String, Object> generateQrCode(@RequestParam String uniqueCode, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        String role = (String) request.getAttribute("currentUserRole");

        if (!"admin".equals(role)) {
            result.put("success", false);
            result.put("message", "仅管理员可生成测试二维码");
            return result;
        }

        if (uniqueCode == null || uniqueCode.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "uniqueCode 参数不能为空");
            return result;
        }

        String qrContent = buildTraceQueryUrl(request, uniqueCode);

        // 生成二维码图片（Base64）
        String base64Image = QrCodeUtil.generateQrCodeImage(qrContent);

        if (base64Image != null) {
            result.put("success", true);
            result.put("message", "二维码生成成功");
            result.put("uniqueCode", uniqueCode);
            result.put("qrContent", qrContent);
            // 返回带前缀的完整 Data URL，方便前端直接显示
            result.put("qrImage", "data:image/png;base64," + base64Image);
        } else {
            result.put("success", false);
            result.put("message", "二维码生成失败");
        }

        return result;
    }

    private String buildTraceQueryUrl(HttpServletRequest request, String uniqueCode) {
        String baseUrl = resolveH5BaseUrl(request);
        String delimiter = baseUrl.contains("?") ? "&" : "?";
        return baseUrl + delimiter + "code=" + URLEncoder.encode(uniqueCode, StandardCharsets.UTF_8);
    }

    private String resolveH5BaseUrl(HttpServletRequest request) {
        if (h5BaseUrl != null && !h5BaseUrl.trim().isEmpty()) {
            return trimTrailingSlash(h5BaseUrl.trim());
        }

        StringBuilder builder = new StringBuilder();
        builder.append(request.getScheme()).append("://").append(request.getServerName());
        boolean useDefaultPort = ("http".equalsIgnoreCase(request.getScheme()) && request.getServerPort() == 80)
                || ("https".equalsIgnoreCase(request.getScheme()) && request.getServerPort() == 443);
        if (!useDefaultPort) {
            builder.append(":").append(request.getServerPort());
        }
        return builder.toString();
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private boolean canManageTrace(String role) {
        return "admin".equals(role) || "enterprise".equals(role);
    }

    private void normalizeLegacyStatus(QrCode qrCode) {
        if (qrCode == null) {
            return;
        }
        String normalizedStatus = QrCodeStatusHelper.normalize(qrCode.getStatus());
        if (!normalizedStatus.equals(qrCode.getStatus())) {
            QrCode update = new QrCode();
            update.setQrId(qrCode.getQrId());
            update.setStatus(normalizedStatus);
            qrCodeMapper.updateById(update);
            qrCode.setStatus(normalizedStatus);
        }
    }
}
