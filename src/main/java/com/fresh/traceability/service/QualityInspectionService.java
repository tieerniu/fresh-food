package com.fresh.traceability.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fresh.traceability.entity.ProductBatch;
import com.fresh.traceability.entity.SecurityWarning;
import com.fresh.traceability.entity.QrCode;
import com.fresh.traceability.entity.QualityInspection;
import com.fresh.traceability.mapper.ProductBatchMapper;
import com.fresh.traceability.mapper.QrCodeMapper;
import com.fresh.traceability.mapper.QualityInspectionMapper;
import com.fresh.traceability.mapper.SecurityWarningMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;

@Service
public class QualityInspectionService {

    @Autowired
    private QualityInspectionMapper inspectionMapper;

    @Autowired
    private QrCodeMapper qrCodeMapper;

    @Autowired
    private ProductBatchMapper productBatchMapper;

    @Autowired
    private SecurityWarningMapper warningMapper;

    @Autowired
    private WarningService warningService;

    @Transactional(rollbackFor = Exception.class)
    public void addInspection(QualityInspection inspection) {
        normalizeInspectionPayload(inspection, null);
        if (inspection.getInspectionDate() == null) {
            inspection.setInspectionDate(LocalDate.now());
        }
        inspectionMapper.insert(inspection);
        postProcessInspection(inspection);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateInspection(QualityInspection inspection) {
        QualityInspection dbInspection = inspectionMapper.selectById(inspection.getInspectionId());
        if (dbInspection == null) {
            return false;
        }

        normalizeInspectionPayload(inspection, dbInspection);
        inspectionMapper.updateById(inspection);
        postProcessInspection(inspection);
        return true;
    }

    private void normalizeInspectionPayload(QualityInspection target, QualityInspection dbInspection) {
        Integer sourceWarningId = target.getSourceWarningId() != null
                ? target.getSourceWarningId()
                : (dbInspection != null ? dbInspection.getSourceWarningId() : null);
        String sourceType = firstNonBlank(target.getSourceType(), dbInspection != null ? dbInspection.getSourceType() : null);
        if (sourceWarningId != null) {
            sourceType = "Warning";
        }
        if (sourceType == null) {
            sourceType = "Routine";
        }

        SecurityWarning sourceWarning = null;
        Integer resolvedBatchId = target.getBatchId() != null
                ? target.getBatchId()
                : (dbInspection != null ? dbInspection.getBatchId() : null);
        if (sourceWarningId != null) {
            sourceWarning = warningMapper.selectById(sourceWarningId);
            if (sourceWarning == null) {
                throw new IllegalArgumentException("关联的预警记录不存在，无法提交质检结论");
            }
            QrCode warningQr = qrCodeMapper.selectByUniqueCode(sourceWarning.getUniqueCode());
            if (warningQr == null || warningQr.getBatchId() == null) {
                throw new IllegalArgumentException("来源预警未关联有效批次，无法创建质检任务");
            }
            if (resolvedBatchId != null && !Objects.equals(resolvedBatchId, warningQr.getBatchId())) {
                throw new IllegalArgumentException("预警联动质检必须绑定原始风险批次，不允许改到其他批次");
            }
            resolvedBatchId = warningQr.getBatchId();
        }

        if (dbInspection != null && !Objects.equals(resolvedBatchId, dbInspection.getBatchId())) {
            throw new IllegalArgumentException("为保证质检审计链稳定，不支持直接修改所属批次");
        }
        if (dbInspection != null && !Objects.equals(sourceWarningId, dbInspection.getSourceWarningId())) {
            throw new IllegalArgumentException("预警联动关系不允许被手动修改");
        }

        String inspectionResult = target.getInspectionResult() != null
                ? target.getInspectionResult()
                : (dbInspection != null ? dbInspection.getInspectionResult() : null);
        Boolean isRecalled = target.getIsRecalled() != null
                ? target.getIsRecalled()
                : (dbInspection != null ? dbInspection.getIsRecalled() : null);
        String inspectionStatus = firstNonBlank(
                target.getInspectionStatus(),
                dbInspection != null ? dbInspection.getInspectionStatus() : null);

        boolean completed = isCompletedStatus(inspectionStatus) || !isBlank(inspectionResult);
        if (!completed && "Routine".equals(sourceType)) {
            throw new IllegalArgumentException("手动新增质检记录时必须填写质检结果");
        }

        validateBatch(resolvedBatchId);

        if (dbInspection != null && Boolean.TRUE.equals(dbInspection.getIsRecalled()) && !Boolean.TRUE.equals(isRecalled)) {
            throw new IllegalArgumentException("召回一旦启动不可撤销，不能将该记录改回未召回");
        }

        if (completed) {
            if ("合格".equals(inspectionResult)) {
                isRecalled = false;
            } else if ("Warning".equals(sourceType) && "不合格".equals(inspectionResult)) {
                isRecalled = true;
            }
            validateCompletedInspection(resolvedBatchId, inspectionResult, isRecalled, sourceType);
            inspectionStatus = "Completed";
        } else {
            inspectionStatus = "Pending";
            inspectionResult = null;
            isRecalled = false;
        }

        if (isBatchRecalled(resolvedBatchId) && !Boolean.TRUE.equals(isRecalled) && "Pending".equals(inspectionStatus)) {
            throw new IllegalArgumentException("该批次已进入召回状态，新增质检任务不能再回退为普通待检");
        }
        if (isBatchRecalled(resolvedBatchId) && "Completed".equals(inspectionStatus)
                && "Warning".equals(sourceType) && !"合格".equals(inspectionResult) && !Boolean.TRUE.equals(isRecalled)) {
            throw new IllegalArgumentException("该批次已进入召回状态，预警联动的不合格质检必须保持召回");
        }

        target.setBatchId(resolvedBatchId);
        target.setSourceWarningId(sourceWarningId);
        target.setSourceType(sourceType);
        target.setInspectionStatus(inspectionStatus);
        target.setInspectionResult(inspectionResult);
        target.setIsRecalled(Boolean.TRUE.equals(isRecalled));

        if (target.getInspectionDate() == null) {
            target.setInspectionDate(dbInspection != null ? dbInspection.getInspectionDate() : LocalDate.now());
        }
        if (dbInspection != null) {
            if (target.getInspector() == null) {
                target.setInspector(dbInspection.getInspector());
            }
            if (target.getInspectionOrg() == null) {
                target.setInspectionOrg(dbInspection.getInspectionOrg());
            }
            if (target.getReportNo() == null) {
                target.setReportNo(dbInspection.getReportNo());
            }
            if (target.getReportUrl() == null) {
                target.setReportUrl(dbInspection.getReportUrl());
            }
            if (target.getRemarks() == null) {
                target.setRemarks(dbInspection.getRemarks());
            }
        }
    }

    private void validateBatch(Integer batchId) {
        if (batchId == null) {
            throw new IllegalArgumentException("请选择产品批次");
        }
        ProductBatch batch = productBatchMapper.selectById(batchId);
        if (batch == null) {
            throw new IllegalArgumentException("产品批次不存在");
        }
    }

    private void validateCompletedInspection(Integer batchId, String inspectionResult, Boolean isRecalled, String sourceType) {
        if (inspectionResult == null || inspectionResult.trim().isEmpty()) {
            throw new IllegalArgumentException("请选择质检结果");
        }
        if (!"合格".equals(inspectionResult) && !"不合格".equals(inspectionResult)) {
            throw new IllegalArgumentException("质检结果仅支持“合格”或“不合格”");
        }
        if (Boolean.TRUE.equals(isRecalled) && !"不合格".equals(inspectionResult)) {
            throw new IllegalArgumentException("启动召回时，质检结果必须为不合格");
        }
        if ("Warning".equals(sourceType) && "不合格".equals(inspectionResult) && !Boolean.TRUE.equals(isRecalled)) {
            throw new IllegalArgumentException("预警触发的质检任务如判定不合格，必须自动升级为召回");
        }
        if (isBatchRecalled(batchId) && "合格".equals(inspectionResult)) {
            throw new IllegalArgumentException("该批次已处于召回状态，不能再录入“合格”结论覆盖风险");
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deleteInspection(Integer inspectionId) {
        QualityInspection dbInspection = inspectionMapper.selectById(inspectionId);
        if (dbInspection == null) {
            return false;
        }
        if (dbInspection.getSourceWarningId() != null) {
            throw new IllegalArgumentException("预警联动生成的质检任务属于风险审计链，不允许删除");
        }
        if (Boolean.TRUE.equals(dbInspection.getIsRecalled())) {
            throw new IllegalArgumentException("召回记录属于关键审计节点，为保证业务闭环不允许删除");
        }
        inspectionMapper.deleteById(inspectionId);
        return true;
    }

    private void postProcessInspection(QualityInspection inspection) {
        if (isCompletedStatus(inspection.getInspectionStatus()) && Boolean.TRUE.equals(inspection.getIsRecalled())) {
            syncRecallStatus(inspection.getBatchId(), true);
        }
        warningService.syncWarningAfterInspection(inspection);
    }

    private void syncRecallStatus(Integer batchId, Boolean isRecalled) {
        if (!Boolean.TRUE.equals(isRecalled)) {
            return;
        }

        UpdateWrapper<QrCode> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("batch_id", batchId);
        QrCode updateData = new QrCode();
        updateData.setStatus("Recalled");
        qrCodeMapper.update(updateData, updateWrapper);
    }

    private boolean isBatchRecalled(Integer batchId) {
        if (batchId == null) {
            return false;
        }

        Long recalledInspectionCount = inspectionMapper.selectCount(
                new QueryWrapper<QualityInspection>().eq("batch_id", batchId).eq("is_recalled", true));
        if (recalledInspectionCount != null && recalledInspectionCount > 0) {
            return true;
        }

        Long recalledQrCount = qrCodeMapper.selectCount(
                new QueryWrapper<QrCode>().eq("batch_id", batchId).eq("status", "Recalled"));
        return recalledQrCount != null && recalledQrCount > 0;
    }

    private boolean isCompletedStatus(String inspectionStatus) {
        return "Completed".equals(inspectionStatus) || "已完成".equals(inspectionStatus);
    }

    private String firstNonBlank(String first, String second) {
        return !isBlank(first) ? first : (!isBlank(second) ? second : null);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
