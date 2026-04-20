package com.fresh.traceability.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fresh.traceability.config.AuthInterceptor;
import com.fresh.traceability.entity.User;
import com.fresh.traceability.mapper.UserMapper;
import com.fresh.traceability.util.MD5Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/login")
public class LoginController {

    @Autowired
    private UserMapper userMapper;

    @PostMapping
    public Map<String, Object> login(@RequestBody Map<String, String> loginData) {
        Map<String, Object> result = new HashMap<>();
        try {
            String username = loginData.get("username");
            String password = loginData.get("password");
            if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
                result.put("code", 400);
                result.put("message", "用户名和密码不能为空");
                return result;
            }

            // 1. 计算 Hash
            String codeHash = MD5Utils.encrypt(password);

            // 2. 查用户
            // 2. 查用户
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("username", username);

// 使用 selectList 获取列表，避免 selectOne 在遇到重复数据时报错
            List<User> userList = userMapper.selectList(queryWrapper);
            User user = null;

            if (userList != null && !userList.isEmpty()) {
                // 取出第一条数据
                user = userList.get(0);
            }

            if (user == null || !codeHash.equals(user.getPasswordHash())) {
                result.put("code", 400);
                result.put("message", "账号或密码错误");
                return result;
            }
            if (Boolean.FALSE.equals(user.getEnabled())) {
                result.put("code", 403);
                result.put("message", "账号已被管理员禁用，请联系平台管理员");
                return result;
            }

            // 3. 登录成功
            String token = AuthInterceptor.createSession(user);
            Map<String, Object> userData = new HashMap<>();
            userData.put("userId", user.getUserId());
            userData.put("username", user.getUsername());
            userData.put("role", user.getRole());
            userData.put("fullName", user.getFullName());

            Map<String, Object> data = new HashMap<>();
            data.put("token", token);
            data.put("user", userData);

            result.put("code", 200);
            result.put("message", "登录成功");
            result.put("data", data);

        } catch (Exception e) {
            result.put("code", 500);
            result.put("message", "系统错误: " + e.getMessage());
        }
        return result;
    }
}
