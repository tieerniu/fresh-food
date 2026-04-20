package com.fresh.traceability.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fresh.traceability.config.AuthInterceptor;
import com.fresh.traceability.entity.User;
import com.fresh.traceability.mapper.UserMapper;
import com.fresh.traceability.util.MD5Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户管理 Controller
 */
@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserMapper userMapper;

    /**
     * 修改密码接口
     */
    @PostMapping("/password")
    public Map<String, Object> changePassword(@RequestBody Map<String, String> params, HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();

        try {
            String oldPassword = params.get("oldPassword");
            String newPassword = params.get("newPassword");
            Integer currentUserId = (Integer) request.getAttribute("currentUserId");

            // 参数验证
            if (oldPassword == null || oldPassword.trim().isEmpty()) {
                result.put("code", 400);
                result.put("message", "旧密码不能为空");
                return result;
            }
            if (newPassword == null || newPassword.trim().isEmpty()) {
                result.put("code", 400);
                result.put("message", "新密码不能为空");
                return result;
            }
            if (newPassword.length() < 6) {
                result.put("code", 400);
                result.put("message", "新密码长度至少 6 位");
                return result;
            }
            if (newPassword.equals(oldPassword)) {
                result.put("code", 400);
                result.put("message", "新密码不能与旧密码相同");
                return result;
            }
            if (currentUserId == null) {
                result.put("code", 401);
                result.put("message", "未登录");
                return result;
            }

            User user = userMapper.selectById(currentUserId);

            if (user == null) {
                result.put("code", 404);
                result.put("message", "用户不存在");
                return result;
            }

            // 验证旧密码
            String encryptedOldPassword = MD5Utils.encrypt(oldPassword);
            if (!encryptedOldPassword.equals(user.getPasswordHash())) {
                result.put("code", 400);
                result.put("message", "旧密码错误");
                return result;
            }

            // 加密新密码并更新
            String encryptedNewPassword = MD5Utils.encrypt(newPassword);
            user.setPasswordHash(encryptedNewPassword);
            user.setUpdatedAt(LocalDateTime.now());

            int rows = userMapper.updateById(user);

            if (rows > 0) {
                AuthInterceptor.removeSessionsByUserId(currentUserId);
                result.put("code", 200);
                result.put("message", "密码修改成功");
            } else {
                result.put("code", 500);
                result.put("message", "密码修改失败");
            }

        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "修改失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/me")
    public Map<String, Object> getCurrentUserInfo(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();

        try {
            Integer currentUserId = (Integer) request.getAttribute("currentUserId");
            if (currentUserId == null) {
                result.put("code", 401);
                result.put("message", "未登录");
                return result;
            }

            User user = userMapper.selectById(currentUserId);

            if (user == null) {
                result.put("code", 404);
                result.put("message", "用户不存在");
                return result;
            }

            // 构建返回数据（不返回密码）
            Map<String, Object> userData = new HashMap<>();
            userData.put("userId", user.getUserId());
            userData.put("username", user.getUsername());
            userData.put("role", user.getRole());
            userData.put("fullName", user.getFullName());
            userData.put("contactInfo", user.getContactInfo());
            userData.put("createdAt", user.getCreatedAt());

            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", userData);

        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询失败: " + e.getMessage());
        }

        return result;
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        String token = (String) request.getAttribute("currentToken");
        AuthInterceptor.removeToken(token);
        result.put("code", 200);
        result.put("message", "退出成功");
        return result;
    }

    @GetMapping("/enterprise-options")
    public Map<String, Object> getEnterpriseOptions(HttpServletRequest request) {
        Map<String, Object> result = new HashMap<>();
        try {
            String role = (String) request.getAttribute("currentUserRole");
            if (!"admin".equals(role)) {
                result.put("code", 403);
                result.put("message", "仅管理员可操作");
                result.put("data", null);
                return result;
            }

            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("role", "enterprise").orderByAsc("full_name").orderByAsc("user_id");
            List<User> users = userMapper.selectList(queryWrapper);

            List<Map<String, Object>> options = new ArrayList<>();
            for (User user : users) {
                Map<String, Object> item = new HashMap<>();
                item.put("userId", user.getUserId());
                item.put("username", user.getUsername());
                item.put("fullName", user.getFullName());
                item.put("contactInfo", user.getContactInfo());
                options.add(item);
            }

            result.put("code", 200);
            result.put("message", "查询成功");
            result.put("data", options);
        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "查询失败: " + e.getMessage());
            result.put("data", null);
        }
        return result;
    }
}
