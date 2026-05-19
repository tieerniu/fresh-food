package com.fresh.traceability.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fresh.traceability.entity.ProductBatch;
import com.fresh.traceability.entity.QrCode;
import com.fresh.traceability.entity.User;
import com.fresh.traceability.mapper.ProductBatchMapper;
import com.fresh.traceability.mapper.QrCodeMapper;
import com.fresh.traceability.mapper.UserMapper;
import com.fresh.traceability.service.ProductBatchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 产品批次管理 Controller
 */
@RestController
@RequestMapping("/api/product")
public class ProductBatchController {

    @Autowired
    private ProductBatchMapper productBatchMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private QrCodeMapper qrCodeMapper;

    @Autowired
    private ProductBatchService productBatchService;

    /**
     * 获取所有产品批次列表（按创建时间倒序）
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
            QueryWrapper<ProductBatch> queryWrapper = new QueryWrapper<>();
            // 供应商角色只能查看自己录入的批次
            if ("enterprise".equals(role) && userId != null) {
                queryWrapper.eq("manufacturer_id", userId);
            }
            queryWrapper.orderByDesc("created_at");
            List<ProductBatch> list = productBatchMapper.selectList(queryWrapper);

            // 回填供应商名称
            for (ProductBatch batch : list) {
                if (batch.getManufacturerId() != null) {
                    User manufacturer = userMapper.selectById(batch.getManufacturerId());
                    if (manufacturer != null) {
                        batch.setManufacturerName(manufacturer.getFullName());
                    }
                }
                Long generatedCount = qrCodeMapper.selectCount(
                        new QueryWrapper<QrCode>().eq("batch_id", batch.getBatchId()));
                batch.setGeneratedQrCount(generatedCount == null ? 0 : generatedCount.intValue());
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
     * 新增产品批次
     */
    @PostMapping("/add")
    public Map<String, Object> addProduct(
            @RequestBody ProductBatch productBatch,
            HttpServletRequest request) {
        return forbidden("正式产品批次必须由批次申报审核通过后自动生成");
    }

    /**
     * 删除产品批次
     */
    @DeleteMapping("/delete/{id}")
    public Map<String, Object> deleteProduct(@PathVariable Integer id, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            ProductBatch dbBatch = productBatchMapper.selectById(id);
            if (dbBatch == null) {
                result.put("code", 404);
                result.put("message", "记录不存在");
                return result;
            }

            String role = currentRole(request);
            Integer userId = currentUserId(request);
            if (!"admin".equals(role)) {
                return forbidden("正式批次删除仅允许管理员操作");
            }

            productBatchService.deleteBatchCascade(id);
            int rows = 1;

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
     * 更新产品批次
     */
    @PutMapping("/update")
    public Map<String, Object> updateProduct(@RequestBody ProductBatch productBatch, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            ProductBatch dbBatch = productBatchMapper.selectById(productBatch.getBatchId());
            if (dbBatch == null) {
                result.put("code", 404);
                result.put("message", "记录不存在");
                result.put("data", null);
                return result;
            }

            String role = currentRole(request);
            Integer userId = currentUserId(request);
            if (!"admin".equals(role)) {
                return forbidden("正式批次修改仅允许管理员操作");
            }

            if (productBatch.getManufacturerId() == null) {
                result.put("code", 400);
                result.put("message", "管理员编辑批次时必须指定所属企业");
                result.put("data", null);
                return result;
            }

            if (!isValidEnterprise(productBatch.getManufacturerId())) {
                result.put("code", 400);
                result.put("message", "请选择有效的企业账号");
                result.put("data", null);
                return result;
            }

            normalizeBatchFields(productBatch);
            String validationMessage = validateBatchBeforeSave(productBatch, dbBatch);
            if (validationMessage != null) {
                result.put("code", 400);
                result.put("message", validationMessage);
                result.put("data", null);
                return result;
            }

            int rows = productBatchMapper.updateById(productBatch);

            if (rows > 0) {
                result.put("code", 200);
                result.put("message", "更新成功");
                result.put("data", productBatch);
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

    private boolean isValidEnterprise(Integer manufacturerId) {
        if (manufacturerId == null) {
            return false;
        }
        User user = userMapper.selectById(manufacturerId);
        return user != null && "enterprise".equals(user.getRole());
    }

    private boolean hasManagementRole(String role) {
        return "admin".equals(role) || "enterprise".equals(role);
    }

    private void normalizeBatchFields(ProductBatch targetBatch) {
        if (targetBatch.getBatchCode() != null) {
            targetBatch.setBatchCode(targetBatch.getBatchCode().trim());
        }
        if (targetBatch.getProductName() != null) {
            targetBatch.setProductName(targetBatch.getProductName().trim());
        }
        if (targetBatch.getOrigin() != null) {
            targetBatch.setOrigin(targetBatch.getOrigin().trim());
        }
    }

    private String validateBatchBeforeSave(ProductBatch targetBatch, ProductBatch dbBatch) {
        if (targetBatch.getBatchCode() == null || targetBatch.getBatchCode().trim().isEmpty()) {
            return "批次号不能为空";
        }
        if (targetBatch.getProductName() == null || targetBatch.getProductName().trim().isEmpty()) {
            return "产品名称不能为空";
        }
        if (targetBatch.getBatchQuantity() == null || targetBatch.getBatchQuantity() <= 0) {
            return "批次数量必须大于 0";
        }

        QueryWrapper<ProductBatch> duplicateQuery = new QueryWrapper<>();
        duplicateQuery.eq("batch_code", targetBatch.getBatchCode().trim());
        if (dbBatch != null && dbBatch.getBatchId() != null) {
            duplicateQuery.ne("batch_id", dbBatch.getBatchId());
        }
        Long duplicateCount = productBatchMapper.selectCount(duplicateQuery);
        if (duplicateCount != null && duplicateCount > 0) {
            return "批次号已存在，请使用新的批次号";
        }

        Integer batchId = dbBatch != null ? dbBatch.getBatchId() : targetBatch.getBatchId();
        if (batchId != null) {
            Long generatedCount = qrCodeMapper.selectCount(new QueryWrapper<QrCode>().eq("batch_id", batchId));
            int generated = generatedCount == null ? 0 : generatedCount.intValue();
            if (targetBatch.getBatchQuantity() < generated) {
                return "批次数量不能小于已生成二维码数量（当前已生成 " + generated + " 个）";
            }
        }
        return null;
    }
}
