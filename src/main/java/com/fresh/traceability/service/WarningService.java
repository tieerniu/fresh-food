package com.fresh.traceability.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fresh.traceability.entity.ProductBatch;
import com.fresh.traceability.entity.QualityInspection;
import com.fresh.traceability.entity.QrCode;
import com.fresh.traceability.entity.SecurityWarning;
import com.fresh.traceability.entity.User;
import com.fresh.traceability.mapper.ProductBatchMapper;
import com.fresh.traceability.mapper.QualityInspectionMapper;
import com.fresh.traceability.mapper.QrCodeMapper;
import com.fresh.traceability.mapper.ScanLogMapper;
import com.fresh.traceability.mapper.SecurityWarningMapper;
import com.fresh.traceability.mapper.UserMapper;
import com.fresh.traceability.util.QrCodeStatusHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WarningService {

    private static final int WARNING_COOLDOWN_HOURS = 6;
    private static final int RECENT_SCAN_WINDOW_MINUTES = 10;
    private static final int ACTIVE_WARNING_SCAN_THRESHOLD = 5;
    private static final int ACTIVE_WARNING_VISITOR_THRESHOLD = 3;
    private static final int ACTIVE_ATTENTION_SCAN_THRESHOLD = 3;
    private static final int ACTIVE_ATTENTION_VISITOR_THRESHOLD = 2;

    @Autowired
    private SecurityWarningMapper warningMapper;

    @Autowired
    private QrCodeMapper qrCodeMapper;

    @Autowired
    private ProductBatchMapper productBatchMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private QualityInspectionMapper inspectionMapper;

    @Autowired
    private ScanLogMapper scanLogMapper;

    public List<SecurityWarning> listWarningsWithContext() {
        QueryWrapper<SecurityWarning> query = new QueryWrapper<>();
        query.orderByDesc("created_at");
        List<SecurityWarning> list = warningMapper.selectList(query);

        Map<Integer, ProductBatch> batchCache = new HashMap<>();
        Map<Integer, User> userCache = new HashMap<>();
        for (SecurityWarning warning : list) {
            enrichWarning(warning, batchCache, userCache);
        }
        return list;
    }

    public void createFrequentScanWarningIfNeeded(QrCode qrCode, ScanRiskAssessment assessment) {
        if (qrCode == null || qrCode.getUniqueCode() == null) {
            return;
        }

        if (assessment == null || !assessment.shouldCreateWarning()) {
            return;
        }

        String status = QrCodeStatusHelper.normalize(qrCode.getStatus());
        qrCode.setStatus(status);
        if (!QrCodeStatusHelper.ACTIVE.equals(status)) {
            return;
        }

        if (warningMapper.countPendingByUniqueCode(qrCode.getUniqueCode()) > 0) {
            return;
        }

        SecurityWarning latestHandled = warningMapper.selectLatestHandledByUniqueCode(qrCode.getUniqueCode());
        if (latestHandled != null && latestHandled.getHandledAt() != null
                && latestHandled.getHandledAt().isAfter(LocalDateTime.now().minusHours(WARNING_COOLDOWN_HOURS))) {
            return;
        }

        warningMapper.insertPendingWarningIfAbsent(
                qrCode.getUniqueCode(),
                assessment.getWarningContent());
    }

    public ScanRiskAssessment assessScanRisk(QrCode qrCode) {
        if (qrCode == null || qrCode.getUniqueCode() == null) {
            return ScanRiskAssessment.normal();
        }

        Long recentScanCount = scanLogMapper.countRecentScans(qrCode.getUniqueCode());
        Long distinctVisitorCount = scanLogMapper.countDistinctRecentVisitors(qrCode.getUniqueCode());
        Long distinctIpCount = scanLogMapper.countDistinctRecentIps(qrCode.getUniqueCode());

        int recentScans = recentScanCount == null ? 0 : recentScanCount.intValue();
        int visitors = distinctVisitorCount == null ? 0 : distinctVisitorCount.intValue();
        int ipCount = distinctIpCount == null ? 0 : distinctIpCount.intValue();
        String status = QrCodeStatusHelper.normalize(qrCode.getStatus());
        qrCode.setStatus(status);

        if (!QrCodeStatusHelper.ACTIVE.equals(status)) {
            return ScanRiskAssessment.normal();
        }

        if (visitors >= ACTIVE_WARNING_VISITOR_THRESHOLD && recentScans >= ACTIVE_WARNING_SCAN_THRESHOLD) {
            return ScanRiskAssessment.warning(
                    "该防伪码在短时间内被多个设备频繁扫描，存在复制传播风险。",
                    buildWarningContent(recentScans, visitors, ipCount));
        }
        if (visitors >= ACTIVE_ATTENTION_VISITOR_THRESHOLD && recentScans >= ACTIVE_ATTENTION_SCAN_THRESHOLD) {
            return ScanRiskAssessment.attention("该防伪码近期被多个访问来源重复查询，请继续留意。");
        }
        return ScanRiskAssessment.normal();
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> processWarning(Integer id, String actionType) {
        SecurityWarning warning = warningMapper.selectById(id);
        if (warning == null) {
            return null;
        }

        if (!isPendingStatus(warning.getStatus())) {
            throw new IllegalArgumentException("该预警已进入处理流程，不能重复发起操作");
        }

        String normalizedAction = actionType == null ? "ignore" : actionType;
        if ("inspect".equals(normalizedAction)) {
            return startInspection(warning);
        }

        warning.setStatus("freeze".equals(normalizedAction) ? "Resolved" : "Ignored");
        warning.setDisposalType("freeze".equals(normalizedAction) ? "Frozen" : "Ignore");
        warning.setHandledAt(LocalDateTime.now());
        warningMapper.updateById(warning);

        if ("freeze".equals(normalizedAction)) {
            UpdateWrapper<QrCode> updateWrapper = new UpdateWrapper<>();
            updateWrapper.eq("unique_code", warning.getUniqueCode());
            QrCode qrCodeUpdate = new QrCode();
            qrCodeUpdate.setStatus("Recalled");
            qrCodeMapper.update(qrCodeUpdate, updateWrapper);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("warningId", warning.getWarningId());
        result.put("status", warning.getStatus());
        result.put("disposalType", warning.getDisposalType());
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteWarning(Integer id) {
        SecurityWarning warning = warningMapper.selectById(id);
        if (warning == null) {
            return false;
        }
        if (warning.getLinkedInspectionId() != null) {
            throw new IllegalArgumentException("该预警已经关联质检任务，属于完整审计链记录，不允许删除");
        }
        return warningMapper.deleteById(id) > 0;
    }

    @Transactional(rollbackFor = Exception.class)
    public int clearHandledWarnings() {
        QueryWrapper<SecurityWarning> queryWrapper = new QueryWrapper<>();
        queryWrapper.notIn("status", "Pending", "待处理", "InInspection", "待质检");
        queryWrapper.isNull("linked_inspection_id");
        return warningMapper.delete(queryWrapper);
    }

    @Transactional(rollbackFor = Exception.class)
    public void syncWarningAfterInspection(QualityInspection inspection) {
        if (inspection == null || inspection.getSourceWarningId() == null) {
            return;
        }

        SecurityWarning warning = warningMapper.selectById(inspection.getSourceWarningId());
        if (warning == null) {
            return;
        }

        warning.setLinkedInspectionId(inspection.getInspectionId());

        if (isInspectionPending(inspection.getInspectionStatus())) {
            warning.setStatus("InInspection");
            warning.setDisposalType("InspectionCreated");
            warning.setHandledAt(null);
        } else {
            warning.setStatus("Resolved");
            warning.setHandledAt(LocalDateTime.now());
            if ("合格".equals(inspection.getInspectionResult())) {
                warning.setDisposalType("VerifiedPass");
            } else if (Boolean.TRUE.equals(inspection.getIsRecalled())) {
                warning.setDisposalType("EscalatedRecall");
            } else {
                warning.setDisposalType("InspectionCompleted");
            }
        }
        warningMapper.updateById(warning);
    }

    private void enrichWarning(SecurityWarning warning, Map<Integer, ProductBatch> batchCache, Map<Integer, User> userCache) {
        QrCode qrCode = qrCodeMapper.selectByUniqueCode(warning.getUniqueCode());
        if (qrCode == null) {
            return;
        }

        warning.setQrId(qrCode.getQrId());
        warning.setBatchId(qrCode.getBatchId());
        warning.setQrStatus(QrCodeStatusHelper.normalize(qrCode.getStatus()));
        warning.setScanCount(qrCode.getScanCount());

        if (qrCode.getBatchId() == null) {
            return;
        }

        ProductBatch batch = batchCache.computeIfAbsent(qrCode.getBatchId(), productBatchMapper::selectById);
        if (batch == null) {
            return;
        }

        warning.setBatchCode(batch.getBatchCode());
        warning.setProductName(batch.getProductName());

        if (batch.getManufacturerId() != null) {
            User user = userCache.computeIfAbsent(batch.getManufacturerId(), userMapper::selectById);
            if (user != null) {
                warning.setManufacturerName(user.getFullName());
            }
        }

        if (warning.getLinkedInspectionId() != null) {
            QualityInspection inspection = inspectionMapper.selectById(warning.getLinkedInspectionId());
            if (inspection != null) {
                warning.setLinkedInspectionStatus(inspection.getInspectionStatus());
                warning.setLinkedInspectionResult(inspection.getInspectionResult());
            }
        }
    }

    private Map<String, Object> startInspection(SecurityWarning warning) {
        if (warning.getLinkedInspectionId() != null) {
            QualityInspection existed = inspectionMapper.selectById(warning.getLinkedInspectionId());
            if (existed != null) {
                Map<String, Object> existedResult = new HashMap<>();
                existedResult.put("warningId", warning.getWarningId());
                existedResult.put("linkedInspectionId", existed.getInspectionId());
                existedResult.put("status", warning.getStatus());
                existedResult.put("disposalType", warning.getDisposalType());
                existedResult.put("message", "该预警已关联质检任务");
                return existedResult;
            }
        }

        QrCode qrCode = qrCodeMapper.selectByUniqueCode(warning.getUniqueCode());
        if (qrCode == null || qrCode.getBatchId() == null) {
            throw new IllegalArgumentException("预警对应的二维码或批次不存在，无法发起质检");
        }

        ProductBatch batch = productBatchMapper.selectById(qrCode.getBatchId());
        if (batch == null) {
            throw new IllegalArgumentException("预警对应批次不存在，无法发起质检");
        }

        QualityInspection inspection = new QualityInspection();
        inspection.setBatchId(batch.getBatchId());
        inspection.setSourceType("Warning");
        inspection.setSourceWarningId(warning.getWarningId());
        inspection.setInspectionStatus("Pending");
        inspection.setInspectionDate(java.time.LocalDate.now());
        inspection.setRemarks(buildWarningInspectionRemark(warning, batch));
        inspection.setIsRecalled(false);
        inspectionMapper.insert(inspection);

        warning.setStatus("InInspection");
        warning.setDisposalType("InspectionCreated");
        warning.setLinkedInspectionId(inspection.getInspectionId());
        warning.setHandledAt(null);
        warningMapper.updateById(warning);

        Map<String, Object> result = new HashMap<>();
        result.put("warningId", warning.getWarningId());
        result.put("linkedInspectionId", inspection.getInspectionId());
        result.put("status", warning.getStatus());
        result.put("disposalType", warning.getDisposalType());
        return result;
    }

    private String buildWarningInspectionRemark(SecurityWarning warning, ProductBatch batch) {
        StringBuilder builder = new StringBuilder();
        builder.append("来源预警#").append(warning.getWarningId())
                .append("：").append(warning.getWarningContent() == null ? "防伪风险核验" : warning.getWarningContent());
        if (batch.getProductName() != null) {
            builder.append("；批次产品：").append(batch.getProductName());
        }
        if (batch.getBatchCode() != null) {
            builder.append(" / ").append(batch.getBatchCode());
        }
        return builder.toString();
    }

    private boolean isPendingStatus(String status) {
        return "Pending".equals(status) || "待处理".equals(status);
    }

    private boolean isInspectionPending(String inspectionStatus) {
        return inspectionStatus == null || "Pending".equals(inspectionStatus) || "待质检".equals(inspectionStatus);
    }

    private String buildWarningContent(int recentScans, int visitors, int ipCount) {
        return "系统风控：该防伪码在短时间内被多个设备频繁扫码（近 " + RECENT_SCAN_WINDOW_MINUTES + " 分钟扫码 " + recentScans
                + " 次，访问来源 " + visitors + " 个，网络来源 " + ipCount + " 个），存在恶意复制风险！";
    }

    public static class ScanRiskAssessment {
        private final String level;
        private final String message;
        private final String warningContent;

        private ScanRiskAssessment(String level, String message, String warningContent) {
            this.level = level;
            this.message = message;
            this.warningContent = warningContent;
        }

        public static ScanRiskAssessment normal() {
            return new ScanRiskAssessment("normal", "", "");
        }

        public static ScanRiskAssessment attention(String message) {
            return new ScanRiskAssessment("attention", message, "");
        }

        public static ScanRiskAssessment warning(String message, String warningContent) {
            return new ScanRiskAssessment("warning", message, warningContent);
        }

        public String getLevel() {
            return level;
        }

        public String getMessage() {
            return message;
        }

        public String getWarningContent() {
            return warningContent;
        }

        public boolean shouldCreateWarning() {
            return "warning".equals(level);
        }
    }
}
