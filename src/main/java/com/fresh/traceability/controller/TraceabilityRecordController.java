package com.fresh.traceability.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fresh.traceability.entity.ProductBatch;
import com.fresh.traceability.entity.TraceabilityRecord;
import com.fresh.traceability.mapper.ProductBatchMapper;
import com.fresh.traceability.mapper.TraceabilityRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 溯源记录管理 Controller
 */
@RestController
@RequestMapping("/api/record")
public class TraceabilityRecordController {

    private static final List<String> STAGE_SEQUENCE = Arrays.asList(
            "Production", "Processing", "QualityCheck", "Packaging", "Transportation", "Sales");

    @Autowired
    private TraceabilityRecordMapper recordMapper;

    @Autowired
    private ProductBatchMapper productBatchMapper;

    /**
     * 获取所有溯源记录列表（按记录时间倒序）
     */
    @GetMapping("/list")
    public Map<String, Object> getList(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String role = currentRole(request);
            Integer userId = currentUserId(request);
            if (!hasManagementRole(role)) {
                return forbidden("仅管理员或企业用户可操作");
            }
            QueryWrapper<TraceabilityRecord> queryWrapper = new QueryWrapper<>();
            if ("enterprise".equals(role) && userId != null) {
                queryWrapper.inSql("batch_id", "SELECT batch_id FROM product_batches WHERE manufacturer_id = " + userId);
            }
            queryWrapper.orderByDesc("recorded_at");
            List<TraceabilityRecord> list = recordMapper.selectList(queryWrapper);

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
     * 新增溯源记录
     */
    @PostMapping("/add")
    public Map<String, Object> addRecord(@RequestBody TraceabilityRecord record, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            Integer userId = currentUserId(request);
            String role = currentRole(request);
            if (userId == null) {
                return unauthorized();
            }
            if (!hasManagementRole(role)) {
                return forbidden("仅管理员或企业用户可操作");
            }

            if (!isValidStage(record.getNodeStage())) {
                result.put("code", 400);
                result.put("message", "溯源环节无效");
                result.put("data", null);
                return result;
            }

            ProductBatch batch = productBatchMapper.selectById(record.getBatchId());
            if (batch == null) {
                result.put("code", 404);
                result.put("message", "批次不存在");
                result.put("data", null);
                return result;
            }
            if ("enterprise".equals(role) && !Objects.equals(batch.getManufacturerId(), userId)) {
                return forbidden("无权给其他企业批次新增溯源记录");
            }

            String sequenceMessage = validateAddStageSequence(record.getBatchId(), record.getNodeStage());
            if (sequenceMessage != null) {
                result.put("code", 400);
                result.put("message", sequenceMessage);
                result.put("data", null);
                return result;
            }

            // 设置记录时间
            record.setRecordedAt(LocalDateTime.now());
            record.setOperatorId(userId);
            int rows = recordMapper.insert(record);

            if (rows > 0) {
                result.put("code", 200);
                result.put("message", "新增成功");
                result.put("data", record);
            } else {
                result.put("code", 500);
                result.put("message", "新增失败");
                result.put("data", null);
            }
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "新增失败: " + e.getMessage());
            result.put("data", null);
        }
        return result;
    }

    /**
     * 删除溯源记录
     */
    @DeleteMapping("/delete/{id}")
    public Map<String, Object> deleteRecord(@PathVariable Integer id, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            TraceabilityRecord dbRecord = recordMapper.selectById(id);
            if (dbRecord == null) {
                result.put("code", 404);
                result.put("message", "记录不存在");
                return result;
            }

            if (!canOperateRecord(request, dbRecord)) {
                return forbidden("无权删除其他企业溯源记录");
            }

            int rows = recordMapper.deleteById(id);

            if (rows > 0) {
                result.put("code", 200);
                result.put("message", "删除成功");
            } else {
                result.put("code", 404);
                result.put("message", "记录不存在");
            }
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "删除失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 更新溯源记录
     */
    @PutMapping("/update")
    public Map<String, Object> updateRecord(@RequestBody TraceabilityRecord record, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String role = currentRole(request);
            Integer userId = currentUserId(request);
            if (!hasManagementRole(role)) {
                return forbidden("仅管理员或企业用户可操作");
            }

            TraceabilityRecord dbRecord = recordMapper.selectById(record.getRecordId());
            if (dbRecord == null) {
                result.put("code", 404);
                result.put("message", "记录不存在");
                result.put("data", null);
                return result;
            }

            if (!canOperateRecord(request, dbRecord)) {
                return forbidden("无权修改其他企业溯源记录");
            }

            Integer targetBatchId = record.getBatchId() != null ? record.getBatchId() : dbRecord.getBatchId();
            if (!Objects.equals(targetBatchId, dbRecord.getBatchId())) {
                return forbidden("为保证时间线顺序，不支持直接修改所属批次，如需调整请删除后重新新增");
            }
            ProductBatch targetBatch = productBatchMapper.selectById(targetBatchId);
            if (targetBatch == null) {
                result.put("code", 404);
                result.put("message", "目标批次不存在");
                result.put("data", null);
                return result;
            }
            if ("enterprise".equals(role) && !Objects.equals(targetBatch.getManufacturerId(), userId)) {
                return forbidden("无权转移到其他企业批次");
            }

            String targetStage = record.getNodeStage() != null ? record.getNodeStage() : dbRecord.getNodeStage();
            if (!isValidStage(targetStage)) {
                result.put("code", 400);
                result.put("message", "溯源环节无效");
                result.put("data", null);
                return result;
            }
            if (!Objects.equals(targetStage, dbRecord.getNodeStage())) {
                return forbidden("为保证时间线顺序，不支持直接修改溯源环节，如需调整请删除后重新新增");
            }

            record.setOperatorId(dbRecord.getOperatorId());
            record.setBatchId(targetBatchId);
            record.setNodeStage(dbRecord.getNodeStage());
            if (record.getRecordedAt() == null) {
                record.setRecordedAt(dbRecord.getRecordedAt());
            }
            int rows = recordMapper.updateById(record);

            if (rows > 0) {
                result.put("code", 200);
                result.put("message", "更新成功");
                result.put("data", record);
            } else {
                result.put("code", 404);
                result.put("message", "记录不存在");
                result.put("data", null);
            }
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "更新失败: " + e.getMessage());
            result.put("data", null);
        }
        return result;
    }

    private boolean canOperateRecord(HttpServletRequest request, TraceabilityRecord record) {
        String role = currentRole(request);
        Integer userId = currentUserId(request);
        if ("admin".equals(role)) {
            return true;
        }
        if (!"enterprise".equals(role)) {
            return false;
        }

        ProductBatch batch = productBatchMapper.selectById(record.getBatchId());
        return batch != null && Objects.equals(batch.getManufacturerId(), userId);
    }

    private boolean hasManagementRole(String role) {
        return "admin".equals(role) || "enterprise".equals(role);
    }

    private boolean isValidStage(String stage) {
        return stage != null && STAGE_SEQUENCE.contains(stage);
    }

    private String validateAddStageSequence(Integer batchId, String targetStage) {
        QueryWrapper<TraceabilityRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("batch_id", batchId).orderByAsc("recorded_at").orderByAsc("record_id");
        List<TraceabilityRecord> existingRecords = recordMapper.selectList(queryWrapper);

        if (existingRecords == null || existingRecords.isEmpty()) {
            return "Production".equals(targetStage) ? null : "首个溯源节点必须从“种植”开始";
        }

        for (TraceabilityRecord item : existingRecords) {
            if (targetStage.equals(item.getNodeStage())) {
                return "同一批次的“" + getStageLabel(targetStage) + "”环节只能录入一次";
            }
        }

        String lastStage = existingRecords.get(existingRecords.size() - 1).getNodeStage();
        int lastOrder = STAGE_SEQUENCE.indexOf(lastStage);
        int targetOrder = STAGE_SEQUENCE.indexOf(targetStage);
        if (lastOrder == -1 || targetOrder == -1) {
            return "溯源环节无效";
        }
        if (lastOrder >= STAGE_SEQUENCE.size() - 1) {
            return "该批次已完成全部标准溯源环节，不能继续新增";
        }
        if (targetOrder != lastOrder + 1) {
            return "请按顺序录入，下一个环节应为“" + getStageLabel(STAGE_SEQUENCE.get(lastOrder + 1)) + "”";
        }
        return null;
    }

    private String getStageLabel(String stage) {
        switch (stage) {
            case "Production":
                return "种植";
            case "Processing":
                return "采摘";
            case "QualityCheck":
                return "质检";
            case "Packaging":
                return "包装";
            case "Transportation":
                return "运输";
            case "Sales":
                return "销售";
            default:
                return stage;
        }
    }

    private Integer currentUserId(HttpServletRequest request) {
        return (Integer) request.getAttribute("currentUserId");
    }

    private String currentRole(HttpServletRequest request) {
        return (String) request.getAttribute("currentUserRole");
    }

    private Map<String, Object> unauthorized() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 401);
        result.put("message", "未登录");
        result.put("data", null);
        return result;
    }

    private Map<String, Object> forbidden(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 403);
        result.put("message", message);
        result.put("data", null);
        return result;
    }
}
