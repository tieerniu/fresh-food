package com.fresh.traceability.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fresh.traceability.entity.QualityInspection;
import com.fresh.traceability.mapper.QualityInspectionMapper;
import com.fresh.traceability.service.QualityInspectionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 质检与缺陷召回 Controller
 */
@RestController
@RequestMapping("/api/inspection")
public class QualityInspectionController {

    @Autowired
    private QualityInspectionMapper inspectionMapper;

    @Autowired
    private QualityInspectionService qualityInspectionService;

    /**
     * 查询质检记录列表（按质检日期倒序）
     */
    @GetMapping("/list")
    public Map<String, Object> list(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }
            QueryWrapper<QualityInspection> query = new QueryWrapper<>();
            query.orderByDesc("inspection_date");
            List<QualityInspection> list = inspectionMapper.selectList(query);
            result.put("code", 200);
            result.put("data", list);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 新增质检记录（含跨表召回联动）
     */
    @PostMapping("/add")
    public Map<String, Object> add(@RequestBody QualityInspection inspection, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }
            qualityInspectionService.addInspection(inspection);

            result.put("code", 200);
            result.put("message", "添加成功");
            result.put("data", inspection);
        } catch (IllegalArgumentException e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "添加失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 修改质检记录（含跨表召回联动）
     */
    @PutMapping("/update")
    public Map<String, Object> update(@RequestBody QualityInspection inspection, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }
            boolean updated = qualityInspectionService.updateInspection(inspection);
            if (!updated) {
                result.put("code", 404);
                result.put("message", "质检记录不存在");
                return result;
            }

            result.put("code", 200);
            result.put("message", "修改成功");
        } catch (IllegalArgumentException e) {
            result.put("code", 400);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "修改失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 删除质检记录
     */
    @DeleteMapping("/delete/{id}")
    public Map<String, Object> delete(@PathVariable Integer id, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (!isAdmin(request)) {
                return forbidden();
            }
            boolean deleted = qualityInspectionService.deleteInspection(id);
            if (!deleted) {
                result.put("code", 404);
                result.put("message", "质检记录不存在");
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
