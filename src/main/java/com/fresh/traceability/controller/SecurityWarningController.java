package com.fresh.traceability.controller;

import com.fresh.traceability.entity.SecurityWarning;
import com.fresh.traceability.service.WarningService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 防伪预警 Controller
 */
@RestController
@RequestMapping("/api/warning")
public class SecurityWarningController {

    @Autowired
    private WarningService warningService;

    /**
     * 查询预警列表（按创建时间倒序）
     */
    @GetMapping("/list")
    public Map<String, Object> list(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }
            List<SecurityWarning> list = warningService.listWarningsWithContext();
            result.put("code", 200);
            result.put("data", list);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 标记预警为已处理
     */
    @PutMapping("/resolve/{id}")
    public Map<String, Object> resolve(
            @PathVariable Integer id,
            @RequestParam(value = "actionType", defaultValue = "ignore") String actionType,
            HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }

            Map<String, Object> data = warningService.processWarning(id, actionType);
            if (data == null) {
                result.put("code", 404);
                result.put("message", "预警记录不存在");
                return result;
            }

            result.put("code", 200);
            result.put("message", "处理成功");
            result.put("data", data);
        } catch (IllegalArgumentException e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "操作失败: " + e.getMessage());
        }
        return result;
    }

    @DeleteMapping("/delete/{id}")
    public Map<String, Object> delete(@PathVariable Integer id, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }

            boolean deleted = warningService.deleteWarning(id);
            if (!deleted) {
                result.put("code", 404);
                result.put("message", "预警记录不存在");
                return result;
            }

            result.put("code", 200);
            result.put("message", "删除成功");
        } catch (IllegalArgumentException e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "删除失败: " + e.getMessage());
        }
        return result;
    }

    @DeleteMapping("/clear-handled")
    public Map<String, Object> clearHandled(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }

            int deletedCount = warningService.clearHandledWarnings();
            result.put("code", 200);
            result.put("message", "已清理 " + deletedCount + " 条历史预警");
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "清理失败: " + e.getMessage());
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
