package com.fresh.traceability.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fresh.traceability.entity.BatchApplication;
import com.fresh.traceability.entity.ProductBatch;
import com.fresh.traceability.entity.User;
import com.fresh.traceability.mapper.BatchApplicationMapper;
import com.fresh.traceability.mapper.ProductBatchMapper;
import com.fresh.traceability.mapper.UserMapper;
import com.fresh.traceability.service.BatchApplicationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/application")
public class BatchApplicationController {

    @Autowired
    private BatchApplicationMapper applicationMapper;

    @Autowired
    private BatchApplicationService applicationService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProductBatchMapper productBatchMapper;

    @GetMapping("/list")
    public Map<String, Object> list(@RequestParam(value = "status", required = false) String status, HttpServletRequest request) {
        try {
            String role = currentRole(request);
            Integer userId = currentUserId(request);
            if (!hasManagementRole(role)) {
                return forbidden("仅管理员或合作基地用户可访问");
            }

            QueryWrapper<BatchApplication> queryWrapper = new QueryWrapper<>();
            if ("enterprise".equals(role)) {
                queryWrapper.eq("manufacturer_id", userId);
            }
            if (status != null && !status.trim().isEmpty() && !"All".equals(status)) {
                queryWrapper.eq("status", status.trim());
            } else {
                queryWrapper.ne("status", BatchApplicationService.STATUS_VOIDED);
            }
            queryWrapper.orderByDesc("created_at").orderByDesc("application_id");
            List<BatchApplication> list = applicationMapper.selectList(queryWrapper);
            fillDisplayFields(list);

            return success(list, "查询成功");
        } catch (Exception e) {
            return error("查询失败: " + e.getMessage());
        }
    }

    @PostMapping("/submit")
    public Map<String, Object> submit(@RequestBody BatchApplication application, HttpServletRequest request) {
        try {
            if (!"enterprise".equals(currentRole(request))) {
                return forbidden("仅合作基地可提交批次申报");
            }
            BatchApplication saved = applicationService.submit(application, currentUserId(request));
            fillDisplayFields(List.of(saved));
            return success(saved, "申报提交成功，等待管理员审核");
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return error("提交失败: " + e.getMessage());
        }
    }

    @PutMapping("/resubmit")
    public Map<String, Object> resubmit(@RequestBody BatchApplication application, HttpServletRequest request) {
        try {
            if (!"enterprise".equals(currentRole(request))) {
                return forbidden("仅合作基地可修改并重新提交申报");
            }
            BatchApplication saved = applicationService.reviseAndResubmit(application, currentUserId(request));
            fillDisplayFields(List.of(saved));
            return success(saved, "已重新提交，等待管理员审核");
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return error("重新提交失败: " + e.getMessage());
        }
    }

    @PostMapping("/approve/{id}")
    public Map<String, Object> approve(@PathVariable Integer id,
                                       @RequestBody(required = false) Map<String, String> params,
                                       HttpServletRequest request) {
        try {
            if (!"admin".equals(currentRole(request))) {
                return forbidden("仅管理员可审核批次申报");
            }
            String opinion = params == null ? null : params.get("reviewOpinion");
            ProductBatch productBatch = applicationService.approveAndConvert(id, currentUserId(request), opinion);
            return success(productBatch, "审核通过，已生成正式产品批次");
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return error("审核失败: " + e.getMessage());
        }
    }

    @PostMapping("/reject/{id}")
    public Map<String, Object> reject(@PathVariable Integer id,
                                      @RequestBody Map<String, String> params,
                                      HttpServletRequest request) {
        try {
            if (!"admin".equals(currentRole(request))) {
                return forbidden("仅管理员可审核批次申报");
            }
            String opinion = params == null ? null : params.get("reviewOpinion");
            applicationService.reject(id, currentUserId(request), opinion);
            return success(null, "已退回补正");
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return error("退回失败: " + e.getMessage());
        }
    }

    @PostMapping("/deny/{id}")
    public Map<String, Object> deny(@PathVariable Integer id,
                                    @RequestBody Map<String, String> params,
                                    HttpServletRequest request) {
        try {
            if (!"admin".equals(currentRole(request))) {
                return forbidden("仅管理员可审核批次申报");
            }
            String opinion = params == null ? null : params.get("reviewOpinion");
            applicationService.deny(id, currentUserId(request), opinion);
            return success(null, "已标记为不予通过");
        } catch (IllegalArgumentException e) {
            return badRequest(e.getMessage());
        } catch (Exception e) {
            return error("不予通过失败: " + e.getMessage());
        }
    }

    private void fillDisplayFields(List<BatchApplication> applications) {
        for (BatchApplication application : applications) {
            if (application.getManufacturerId() != null) {
                User manufacturer = userMapper.selectById(application.getManufacturerId());
                if (manufacturer != null) {
                    application.setManufacturerName(manufacturer.getFullName() != null
                            ? manufacturer.getFullName()
                            : manufacturer.getUsername());
                }
            }
            if (application.getReviewerId() != null) {
                User reviewer = userMapper.selectById(application.getReviewerId());
                if (reviewer != null) {
                    application.setReviewerName(reviewer.getFullName() != null
                            ? reviewer.getFullName()
                            : reviewer.getUsername());
                }
            }
            if (application.getConvertedBatchId() != null) {
                ProductBatch batch = productBatchMapper.selectById(application.getConvertedBatchId());
                if (batch != null) {
                    application.setConvertedBatchCode(batch.getBatchCode());
                }
            }
        }
    }

    private boolean hasManagementRole(String role) {
        return "admin".equals(role) || "enterprise".equals(role);
    }

    private Integer currentUserId(HttpServletRequest request) {
        return (Integer) request.getAttribute("currentUserId");
    }

    private String currentRole(HttpServletRequest request) {
        return (String) request.getAttribute("currentUserRole");
    }

    private Map<String, Object> success(Object data, String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 200);
        result.put("message", message);
        result.put("data", data);
        return result;
    }

    private Map<String, Object> badRequest(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 400);
        result.put("message", message);
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

    private Map<String, Object> error(String message) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", message);
        result.put("data", null);
        return result;
    }
}
