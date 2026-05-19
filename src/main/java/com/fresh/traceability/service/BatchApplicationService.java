package com.fresh.traceability.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fresh.traceability.entity.BatchApplication;
import com.fresh.traceability.entity.ProductBatch;
import com.fresh.traceability.entity.User;
import com.fresh.traceability.mapper.BatchApplicationMapper;
import com.fresh.traceability.mapper.ProductBatchMapper;
import com.fresh.traceability.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class BatchApplicationService {

    public static final String STATUS_PENDING = "Pending";
    public static final String STATUS_REJECTED = "Rejected";
    public static final String STATUS_DENIED = "Denied";
    public static final String STATUS_CONVERTED = "Converted";
    public static final String STATUS_VOIDED = "Voided";

    @Autowired
    private BatchApplicationMapper applicationMapper;

    @Autowired
    private ProductBatchMapper productBatchMapper;

    @Autowired
    private UserMapper userMapper;

    public BatchApplication submit(BatchApplication application, Integer userId) {
        if (!isValidEnterprise(userId)) {
            throw new IllegalArgumentException("仅有效合作基地账号可提交批次申报");
        }

        normalizeApplication(application);
        validateApplication(application);

        LocalDateTime now = LocalDateTime.now();
        application.setApplicationId(null);
        application.setApplicationNo(generateApplicationNo());
        application.setManufacturerId(userId);
        application.setCreatedBy(userId);
        application.setStatus(STATUS_PENDING);
        application.setReviewOpinion(null);
        application.setReviewerId(null);
        application.setReviewTime(null);
        application.setConvertedBatchId(null);
        application.setCreatedAt(now);
        application.setUpdatedAt(now);
        applicationMapper.insert(application);
        return application;
    }

    public BatchApplication reviseAndResubmit(BatchApplication application, Integer userId) {
        if (application.getApplicationId() == null) {
            throw new IllegalArgumentException("申报ID不能为空");
        }
        BatchApplication dbApplication = applicationMapper.selectById(application.getApplicationId());
        if (dbApplication == null) {
            throw new IllegalArgumentException("申报记录不存在");
        }
        if (!userId.equals(dbApplication.getManufacturerId())) {
            throw new IllegalArgumentException("无权修改其他基地的申报");
        }
        if (!STATUS_REJECTED.equals(dbApplication.getStatus())) {
            throw new IllegalArgumentException("仅退回补正的申报可以修改后重新提交");
        }

        normalizeApplication(application);
        validateApplication(application);

        LocalDateTime now = LocalDateTime.now();
        UpdateWrapper<BatchApplication> updateWrapper = new UpdateWrapper<>();
        updateWrapper.eq("application_id", dbApplication.getApplicationId())
                .set("product_name", application.getProductName())
                .set("origin", application.getOrigin())
                .set("production_date", application.getProductionDate())
                .set("shelf_life_days", application.getShelfLifeDays())
                .set("batch_quantity", application.getBatchQuantity())
                .set("production_manager", application.getProductionManager())
                .set("manager_phone", application.getManagerPhone())
                .set("production_address", application.getProductionAddress())
                .set("expected_market_date", application.getExpectedMarketDate())
                .set("quality_commitment", application.getQualityCommitment())
                .set("description", application.getDescription())
                .set("image_url", application.getImageUrl())
                .set("status", STATUS_PENDING)
                .set("review_opinion", null)
                .set("reviewer_id", null)
                .set("review_time", null)
                .set("updated_at", now);
        applicationMapper.update(null, updateWrapper);
        return applicationMapper.selectById(dbApplication.getApplicationId());
    }

    @Transactional(rollbackFor = Exception.class)
    public ProductBatch approveAndConvert(Integer applicationId, Integer reviewerId, String reviewOpinion) {
        BatchApplication application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new IllegalArgumentException("申报记录不存在");
        }
        if (!STATUS_PENDING.equals(application.getStatus())) {
            throw new IllegalArgumentException("只有待审核申报可以审核通过");
        }
        if (!isValidEnterprise(application.getManufacturerId())) {
            throw new IllegalArgumentException("申报所属基地账号无效");
        }
        validateApplication(application);

        ProductBatch productBatch = new ProductBatch();
        productBatch.setBatchCode(generateBatchCode());
        productBatch.setProductName(application.getProductName());
        productBatch.setOrigin(application.getOrigin());
        productBatch.setProductionDate(application.getProductionDate());
        productBatch.setShelfLifeDays(application.getShelfLifeDays());
        productBatch.setBatchQuantity(application.getBatchQuantity());
        productBatch.setManufacturerId(application.getManufacturerId());
        productBatch.setDescription(application.getDescription());
        productBatch.setImageUrl(application.getImageUrl());
        productBatch.setCreatedAt(LocalDateTime.now());
        productBatchMapper.insert(productBatch);

        LocalDateTime now = LocalDateTime.now();
        BatchApplication update = new BatchApplication();
        update.setApplicationId(applicationId);
        update.setStatus(STATUS_CONVERTED);
        update.setReviewOpinion(trimToNull(reviewOpinion) == null ? "审核通过，已生成正式产品批次" : reviewOpinion.trim());
        update.setReviewerId(reviewerId);
        update.setReviewTime(now);
        update.setConvertedBatchId(productBatch.getBatchId());
        update.setUpdatedAt(now);
        applicationMapper.updateById(update);

        return productBatch;
    }

    public void reject(Integer applicationId, Integer reviewerId, String reviewOpinion) {
        BatchApplication application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new IllegalArgumentException("申报记录不存在");
        }
        if (!STATUS_PENDING.equals(application.getStatus())) {
            throw new IllegalArgumentException("只有待审核申报可以退回补正");
        }
        if (trimToNull(reviewOpinion) == null) {
            throw new IllegalArgumentException("退回补正时必须填写补正意见");
        }

        BatchApplication update = new BatchApplication();
        update.setApplicationId(applicationId);
        update.setStatus(STATUS_REJECTED);
        update.setReviewOpinion(reviewOpinion.trim());
        update.setReviewerId(reviewerId);
        update.setReviewTime(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        applicationMapper.updateById(update);
    }

    public void deny(Integer applicationId, Integer reviewerId, String reviewOpinion) {
        BatchApplication application = applicationMapper.selectById(applicationId);
        if (application == null) {
            throw new IllegalArgumentException("申报记录不存在");
        }
        if (!STATUS_PENDING.equals(application.getStatus())) {
            throw new IllegalArgumentException("只有待审核申报可以不予通过");
        }
        if (trimToNull(reviewOpinion) == null) {
            throw new IllegalArgumentException("不予通过时必须填写审核意见");
        }

        BatchApplication update = new BatchApplication();
        update.setApplicationId(applicationId);
        update.setStatus(STATUS_DENIED);
        update.setReviewOpinion(reviewOpinion.trim());
        update.setReviewerId(reviewerId);
        update.setReviewTime(LocalDateTime.now());
        update.setUpdatedAt(LocalDateTime.now());
        applicationMapper.updateById(update);
    }

    private void normalizeApplication(BatchApplication application) {
        application.setProductName(trimToNull(application.getProductName()));
        application.setOrigin(trimToNull(application.getOrigin()));
        application.setDescription(trimToNull(application.getDescription()));
        application.setImageUrl(trimToNull(application.getImageUrl()));
        application.setProductionManager(trimToNull(application.getProductionManager()));
        application.setManagerPhone(trimToNull(application.getManagerPhone()));
        application.setProductionAddress(trimToNull(application.getProductionAddress()));
    }

    private void validateApplication(BatchApplication application) {
        if (application.getProductName() == null) {
            throw new IllegalArgumentException("产品名称不能为空");
        }
        if (application.getOrigin() == null) {
            throw new IllegalArgumentException("产地不能为空");
        }
        if (application.getProductionDate() == null) {
            throw new IllegalArgumentException("生产日期不能为空");
        }
        if (application.getShelfLifeDays() == null || application.getShelfLifeDays() <= 0) {
            throw new IllegalArgumentException("保质期必须大于 0 天");
        }
        if (application.getBatchQuantity() == null || application.getBatchQuantity() <= 0) {
            throw new IllegalArgumentException("申报数量必须大于 0");
        }
        if (application.getProductionManager() == null) {
            throw new IllegalArgumentException("生产负责人不能为空");
        }
        if (application.getManagerPhone() == null) {
            throw new IllegalArgumentException("负责人联系电话不能为空");
        }
        if (application.getProductionAddress() == null) {
            throw new IllegalArgumentException("生产地址不能为空");
        }
        if (application.getExpectedMarketDate() == null) {
            throw new IllegalArgumentException("预计上市日期不能为空");
        }
        if (application.getExpectedMarketDate().isBefore(application.getProductionDate())) {
            throw new IllegalArgumentException("预计上市日期不能早于生产日期");
        }
        if (!Boolean.TRUE.equals(application.getQualityCommitment())) {
            throw new IllegalArgumentException("提交申报前必须确认质检与安全承诺");
        }
    }

    private boolean isValidEnterprise(Integer userId) {
        if (userId == null) {
            return false;
        }
        User user = userMapper.selectById(userId);
        return user != null && "enterprise".equals(user.getRole()) && !Boolean.FALSE.equals(user.getEnabled());
    }

    private String generateApplicationNo() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd-HHmmss");
        for (int i = 0; i < 10; i++) {
            String candidate = "BA-" + LocalDateTime.now().format(formatter) + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
            Long count = applicationMapper.selectCount(new QueryWrapper<BatchApplication>().eq("application_no", candidate));
            if (count == null || count == 0) {
                return candidate;
            }
        }
        return "BA-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private String generateBatchCode() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMdd-HHmmss");
        for (int i = 0; i < 10; i++) {
            String candidate = "PB-" + LocalDateTime.now().format(formatter) + "-"
                    + UUID.randomUUID().toString().replace("-", "").substring(0, 3).toUpperCase();
            Long count = productBatchMapper.selectCount(new QueryWrapper<ProductBatch>().eq("batch_code", candidate));
            if (count == null || count == 0) {
                return candidate;
            }
        }
        return "PB-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
