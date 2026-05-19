package com.fresh.traceability.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fresh.traceability.entity.QrCode;
import com.fresh.traceability.entity.QualityInspection;
import com.fresh.traceability.entity.ScanLog;
import com.fresh.traceability.entity.SecurityWarning;
import com.fresh.traceability.entity.TraceabilityRecord;
import com.fresh.traceability.entity.BatchApplication;
import com.fresh.traceability.mapper.BatchApplicationMapper;
import com.fresh.traceability.mapper.ProductBatchMapper;
import com.fresh.traceability.mapper.QrCodeMapper;
import com.fresh.traceability.mapper.QualityInspectionMapper;
import com.fresh.traceability.mapper.ScanLogMapper;
import com.fresh.traceability.mapper.SecurityWarningMapper;
import com.fresh.traceability.mapper.TraceabilityRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ProductBatchService {

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

    @Autowired
    private QualityInspectionMapper inspectionMapper;

    @Autowired
    private BatchApplicationMapper applicationMapper;

    @Transactional(rollbackFor = Exception.class)
    public void deleteBatchCascade(Integer batchId) {
        List<QrCode> qrCodes = qrCodeMapper.selectList(new QueryWrapper<QrCode>().eq("batch_id", batchId));

        for (QrCode qrCode : qrCodes) {
            scanLogMapper.delete(new QueryWrapper<ScanLog>().eq("qr_code_id", qrCode.getQrId()));
            warningMapper.delete(new QueryWrapper<SecurityWarning>().eq("unique_code", qrCode.getUniqueCode()));
        }

        recordMapper.delete(new QueryWrapper<TraceabilityRecord>().eq("batch_id", batchId));
        inspectionMapper.delete(new QueryWrapper<QualityInspection>().eq("batch_id", batchId));
        qrCodeMapper.delete(new QueryWrapper<QrCode>().eq("batch_id", batchId));
        productBatchMapper.deleteById(batchId);

        applicationMapper.update(
                null,
                new UpdateWrapper<BatchApplication>()
                        .eq("converted_batch_id", batchId)
                        .set("status", BatchApplicationService.STATUS_VOIDED)
                        .set("review_opinion", "关联正式产品批次已被管理员删除，申报记录已作废")
                        .set("updated_at", LocalDateTime.now())
        );
    }
}
