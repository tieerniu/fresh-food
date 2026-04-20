package com.fresh.traceability.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fresh.traceability.entity.ProductBatch;
import com.fresh.traceability.entity.QrCode;
import com.fresh.traceability.entity.ScanLog;
import com.fresh.traceability.entity.SecurityWarning;
import com.fresh.traceability.mapper.ProductBatchMapper;
import com.fresh.traceability.mapper.QrCodeMapper;
import com.fresh.traceability.mapper.ScanLogMapper;
import com.fresh.traceability.mapper.SecurityWarningMapper;
import com.fresh.traceability.util.QrCodeStatusHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 二维码管理 Controller
 */
@RestController
@RequestMapping("/api/qrcode")
public class QrCodeController {

    @Autowired
    private QrCodeMapper qrCodeMapper;

    @Autowired
    private SecurityWarningMapper warningMapper;

    @Autowired
    private ProductBatchMapper productBatchMapper;

    @Autowired
    private ScanLogMapper scanLogMapper;

    /**
     * 获取所有二维码列表（按创建时间倒序）
     */
    @GetMapping("/list")
    public Map<String, Object> getList(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }
            QueryWrapper<QrCode> queryWrapper = new QueryWrapper<>();
            queryWrapper.orderByDesc("created_at");
            List<QrCode> list = qrCodeMapper.selectList(queryWrapper);
            for (QrCode qrCode : list) {
                normalizeLegacyStatus(qrCode);
            }

            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", list);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询失败: " + e.getMessage());
            result.put("data", null);
        }
        return result;
    }

    /**
     * 生成新的二维码
     */
    @PostMapping("/generate")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> generateQrCode(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }
            Integer batchId = (Integer) params.get("batchId");
            Integer count = params.get("count") != null ? (Integer) params.get("count") : 1;

            if (batchId == null) {
                result.put("code", 400);
                result.put("message", "请选择产品批次");
                return result;
            }
            if (count == null || count < 1 || count > 100) {
                result.put("code", 400);
                result.put("message", "生成数量必须在 1 到 100 之间");
                return result;
            }

            ProductBatch batch = productBatchMapper.selectById(batchId);
            if (batch == null) {
                result.put("code", 404);
                result.put("message", "产品批次不存在");
                return result;
            }
            if (batch.getBatchQuantity() == null || batch.getBatchQuantity() <= 0) {
                result.put("code", 400);
                result.put("message", "该批次尚未设置有效的批次数量，请先完善批次信息");
                return result;
            }

            Long generatedCount = qrCodeMapper.selectCount(new QueryWrapper<QrCode>().eq("batch_id", batchId));
            int existingCount = generatedCount == null ? 0 : generatedCount.intValue();
            int remainingCount = batch.getBatchQuantity() - existingCount;
            if (remainingCount <= 0) {
                result.put("code", 400);
                result.put("message", "该批次二维码已发完，不能继续生成");
                return result;
            }
            if (count > remainingCount) {
                result.put("code", 400);
                result.put("message", "超出该批次可生成上限，当前仅剩 " + remainingCount + " 个可生成二维码");
                return result;
            }

            // 批量生成二维码
            for (int i = 0; i < count; i++) {
                QrCode qrCode = new QrCode();
                qrCode.setBatchId(batchId);
                qrCode.setUniqueCode(generateUniqueCode());
                qrCode.setSecurityToken(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
                qrCode.setStatus("Active");
                qrCode.setScanCount(0);
                qrCode.setCreatedAt(LocalDateTime.now());

                qrCodeMapper.insert(qrCode);
            }

            result.put("code", 200);
            result.put("message", "成功生成 " + count + " 个二维码");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "生成失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 生成唯一码
     */
    private String generateUniqueCode() {
        for (int i = 0; i < 10; i++) {
            String candidate = "QR" + System.currentTimeMillis()
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
            long count = qrCodeMapper.selectCount(new QueryWrapper<QrCode>().eq("unique_code", candidate));
            if (count == 0) {
                return candidate;
            }
        }
        return "QR" + UUID.randomUUID().toString().replace("-", "").toUpperCase();
    }

    /**
     * 更新二维码状态
     */
    @PutMapping("/updateStatus")
    public Map<String, Object> updateStatus(@RequestBody Map<String, Object> params, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }
            Integer qrId = (Integer) params.get("qrId");
            String requestedStatus = (String) params.get("status");
            String targetStatus = QrCodeStatusHelper.normalize(requestedStatus);
            if (qrId == null || requestedStatus == null || !QrCodeStatusHelper.isManagedStatus(requestedStatus)) {
                result.put("code", 400);
                result.put("message", "二维码状态参数无效");
                return result;
            }

            QrCode qrCode = qrCodeMapper.selectById(qrId);
            if (qrCode == null) {
                result.put("code", 404);
                result.put("message", "二维码不存在");
                return result;
            }

            normalizeLegacyStatus(qrCode);

            String currentStatus = QrCodeStatusHelper.normalize(qrCode.getStatus());
            if (!canTransition(currentStatus, targetStatus)) {
                result.put("code", 400);
                result.put("message", "当前状态不允许变更为 " + targetStatus + "，可选状态: " + String.join(", ", getAllowedTargets(currentStatus)));
                return result;
            }

            if (targetStatus.equals(currentStatus)) {
                qrCode.setStatus(currentStatus);
                result.put("code", 200);
                result.put("message", "状态未发生变化");
                result.put("data", qrCode);
                return result;
            }

            qrCode.setStatus(targetStatus);
            qrCodeMapper.updateById(qrCode);

            result.put("code", 200);
            result.put("message", "状态更新成功");
            result.put("data", qrCode);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "更新失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 删除二维码
     */
    @DeleteMapping("/delete/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deleteQrCode(@PathVariable Integer id, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }
            // 先查询二维码，获取 uniqueCode
            QrCode qrCode = qrCodeMapper.selectById(id);
            if (qrCode == null) {
                result.put("code", 404);
                result.put("message", "二维码不存在");
                return result;
            }

            // 联动删除：清理该 uniqueCode 关联的所有预警记录
            QueryWrapper<SecurityWarning> warnQuery = new QueryWrapper<>();
            warnQuery.eq("unique_code", qrCode.getUniqueCode());
            warningMapper.delete(warnQuery);

            QueryWrapper<ScanLog> logQuery = new QueryWrapper<>();
            logQuery.eq("qr_code_id", qrCode.getQrId());
            scanLogMapper.delete(logQuery);

            // 删除二维码本身
            qrCodeMapper.deleteById(id);

            result.put("code", 200);
            result.put("message", "删除成功");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "删除失败: " + e.getMessage());
        }
        return result;
    }

    private boolean isAdmin(HttpServletRequest request) {
        return "admin".equals(request.getAttribute("currentUserRole"));
    }

    private boolean canTransition(String currentStatus, String targetStatus) {
        return QrCodeStatusHelper.canTransition(currentStatus, targetStatus);
    }

    private List<String> getAllowedTargets(String currentStatus) {
        return new ArrayList<>(QrCodeStatusHelper.getAllowedTargets(currentStatus));
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

    private Map<String, Object> forbidden() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 403);
        result.put("message", "仅管理员可操作");
        return result;
    }
}
