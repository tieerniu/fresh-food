package com.fresh.traceability.controller;

import com.fresh.traceability.mapper.ProductBatchMapper;
import com.fresh.traceability.mapper.TraceabilityRecordMapper;
import com.fresh.traceability.mapper.QrCodeMapper;
import com.fresh.traceability.mapper.ScanLogMapper;
import com.fresh.traceability.mapper.SecurityWarningMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fresh.traceability.entity.ProductBatch;
import com.fresh.traceability.entity.QrCode;
import com.fresh.traceability.entity.SecurityWarning;
import com.fresh.traceability.entity.TraceabilityRecord;
import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 控制台统计 Controller
 */
@RestController
@RequestMapping("api/dashboard")
public class DashboardController {

    @Autowired
    private ProductBatchMapper productBatchMapper;

    @Autowired
    private TraceabilityRecordMapper recordMapper;

    @Autowired
    private QrCodeMapper qrCodeMapper;

    @Autowired
    private ScanLogMapper scanLogMapper;

    @Autowired
    private SecurityWarningMapper warningMapper;

    /**
     * 获取控制台统计数据
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String role = (String) request.getAttribute("currentUserRole");
            Integer userId = (Integer) request.getAttribute("currentUserId");
            if (!hasDashboardRole(role)) {
                result.put("code", 403);
                result.put("message", "仅管理员或企业用户可访问");
                result.put("data", null);
                return result;
            }
            QueryWrapper<ProductBatch> batchWrapper = new QueryWrapper<>();
            QueryWrapper<TraceabilityRecord> recordWrapper = new QueryWrapper<>();
            QueryWrapper<QrCode> qrWrapper = new QueryWrapper<>();
            QueryWrapper<QrCode> recalledQrWrapper = new QueryWrapper<>();
            QueryWrapper<QrCode> riskQrWrapper = new QueryWrapper<>();
            QueryWrapper<SecurityWarning> warningWrapper = new QueryWrapper<>();

            if ("enterprise".equals(role) && userId != null) {
                batchWrapper.eq("manufacturer_id", userId);
                recordWrapper.inSql("batch_id", "SELECT batch_id FROM product_batches WHERE manufacturer_id = " + userId);
                qrWrapper.inSql("batch_id", "SELECT batch_id FROM product_batches WHERE manufacturer_id = " + userId);
                recalledQrWrapper.inSql("batch_id", "SELECT batch_id FROM product_batches WHERE manufacturer_id = " + userId);
                riskQrWrapper.inSql("batch_id", "SELECT batch_id FROM product_batches WHERE manufacturer_id = " + userId);
                warningWrapper.inSql(
                        "unique_code",
                        "SELECT q.unique_code FROM qr_codes q JOIN product_batches p ON q.batch_id = p.batch_id WHERE p.manufacturer_id = " + userId);
            }

            // 统计各表数据量
            Long productCount = productBatchMapper.selectCount(batchWrapper);
            Long recordCount = recordMapper.selectCount(recordWrapper);
            Long qrCodeCount = qrCodeMapper.selectCount(qrWrapper);
            recalledQrWrapper.eq("status", "Recalled");
            riskQrWrapper.ge("scan_count", 5);
            warningWrapper.in("status", "Pending", "待处理");
            Long recalledQrCount = qrCodeMapper.selectCount(recalledQrWrapper);
            Long riskCodeCount = qrCodeMapper.selectCount(riskQrWrapper);
            Long pendingWarningCount = warningMapper.selectCount(warningWrapper);

            // 统计今日扫码次数（从 scan_logs 表查询）
            Long todayQuery = 0L;
            try {
                if ("enterprise".equals(role) && userId != null) {
                    todayQuery = scanLogMapper.countTodayScansByManufacturer(userId);
                } else {
                    todayQuery = scanLogMapper.countTodayScans();
                }
            } catch (Exception e) {
                // 如果表不存在或查询失败，返回 0
                System.err.println("统计今日扫码失败: " + e.getMessage());
            }

            Map<String, Object> data = new HashMap<>();
            data.put("productCount", productCount);
            data.put("recordCount", recordCount);
            data.put("qrCodeCount", qrCodeCount);
            data.put("todayQuery", todayQuery);
            data.put("pendingWarningCount", pendingWarningCount);
            data.put("recalledQrCount", recalledQrCount);
            data.put("riskCodeCount", riskCodeCount);

            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", data);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询失败: " + e.getMessage());
            result.put("data", null);
        }
        return result;
    }

    /**
     * 获取图表数据（近7日扫码趋势 + 二维码状态分布）
     */
    @GetMapping("/chart-data")
    public Map<String, Object> getChartData(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String role = (String) request.getAttribute("currentUserRole");
            Integer userId = (Integer) request.getAttribute("currentUserId");
            if (!hasDashboardRole(role)) {
                result.put("code", 403);
                result.put("message", "仅管理员或企业用户可访问");
                result.put("data", null);
                return result;
            }
            Map<String, Object> data = new HashMap<>();

            // 近7日每日扫码趋势
            try {
                List<Map<String, Object>> scanTrend;
                if ("enterprise".equals(role) && userId != null) {
                    scanTrend = scanLogMapper.countByDayRecent7ByManufacturer(userId);
                } else {
                    scanTrend = scanLogMapper.countByDayRecent7();
                }
                data.put("scanTrend", scanTrend);
            } catch (Exception e) {
                System.err.println("查询扫码趋势失败: " + e.getMessage());
                data.put("scanTrend", List.of());
            }

            // 二维码状态分布
            try {
                List<Map<String, Object>> qrStatusDist;
                if ("enterprise".equals(role) && userId != null) {
                    qrStatusDist = qrCodeMapper.countByStatusByManufacturer(userId);
                } else {
                    qrStatusDist = qrCodeMapper.countByStatus();
                }
                data.put("qrStatusDist", qrStatusDist);
            } catch (Exception e) {
                System.err.println("查询二维码状态分布失败: " + e.getMessage());
                data.put("qrStatusDist", List.of());
            }

            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", data);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询失败: " + e.getMessage());
            result.put("data", null);
        }
        return result;
    }

    private boolean hasDashboardRole(String role) {
        return "admin".equals(role) || "enterprise".equals(role);
    }
}
