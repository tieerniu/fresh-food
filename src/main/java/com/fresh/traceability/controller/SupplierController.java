package com.fresh.traceability.controller;

import com.fresh.traceability.entity.Supplier;
import com.fresh.traceability.service.SupplierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 合作基地/供应商 Controller
 */
@RestController
@RequestMapping("/api/supplier")
public class SupplierController {

    @Autowired
    private SupplierService supplierService;

    /**
     * 查询所有供应商
     */
    @GetMapping("/list")
    public Map<String, Object> list(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }
            List<Supplier> list = supplierService.listWithAccountInfo();
            result.put("code", 200);
            result.put("data", list);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 新增供应商（同时自动创建关联的 enterprise 登录账号）
     */
    @PostMapping("/add")
    public Map<String, Object> add(@RequestBody Supplier supplier, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }
            Map<String, Object> data = supplierService.createSupplierWithAccount(supplier);

            result.put("code", 200);
            result.put("message", "添加成功，已自动创建登录账号");
            result.put("data", data);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "添加失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 修改供应商
     */
    @PutMapping("/update")
    public Map<String, Object> update(@RequestBody Supplier supplier, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }
            boolean updated = supplierService.updateSupplierAndAccount(supplier);
            if (!updated) {
                result.put("code", 404);
                result.put("message", "基地不存在");
                return result;
            }
            result.put("code", 200);
            result.put("message", "修改成功");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "修改失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 重置基地账号密码
     */
    @PostMapping("/reset-password/{id}")
    public Map<String, Object> resetPassword(@PathVariable Integer id, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }
            Map<String, Object> data = supplierService.resetSupplierAccountPassword(id);
            result.put("code", 200);
            result.put("message", "密码已重置");
            result.put("data", data);
        } catch (IllegalArgumentException e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "重置失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 启用/禁用基地账号
     */
    @PutMapping("/account-status/{id}")
    public Map<String, Object> updateAccountStatus(
            @PathVariable Integer id,
            @RequestBody Map<String, Boolean> params,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }
            Boolean enabled = params.get("enabled");
            Map<String, Object> data = supplierService.updateSupplierAccountStatus(id, enabled);
            result.put("code", 200);
            result.put("message", data.get("message"));
            result.put("data", data);
        } catch (IllegalArgumentException e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "状态更新失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 删除供应商
     */
    @DeleteMapping("/delete/{id}")
    public Map<String, Object> delete(@PathVariable Integer id, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }
            Map<String, Object> serviceResult = supplierService.deleteSupplierCascade(id);
            if (Boolean.TRUE.equals(serviceResult.get("deleted"))) {
                result.put("code", 200);
                result.put("message", serviceResult.get("message"));
            } else {
                result.put("code", 400);
                result.put("message", serviceResult.get("message"));
                result.put("data", serviceResult);
            }
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "删除失败: " + e.getMessage());
        }
        return result;
    }

    private boolean isAdmin(HttpServletRequest request) {
        return "admin".equals(request.getAttribute("currentUserRole"));
    }

    private Map<String, Object> forbidden() {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 403);
        result.put("message", "仅管理员可操作");
        return result;
    }
}
