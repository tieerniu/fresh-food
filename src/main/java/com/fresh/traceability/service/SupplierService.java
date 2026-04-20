package com.fresh.traceability.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fresh.traceability.config.AuthInterceptor;
import com.fresh.traceability.entity.ProductBatch;
import com.fresh.traceability.entity.Supplier;
import com.fresh.traceability.entity.User;
import com.fresh.traceability.mapper.ProductBatchMapper;
import com.fresh.traceability.mapper.SupplierMapper;
import com.fresh.traceability.mapper.UserMapper;
import com.fresh.traceability.util.MD5Utils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SupplierService {

    @Autowired
    private SupplierMapper supplierMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ProductBatchMapper productBatchMapper;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createSupplierWithAccount(Supplier supplier) {
        supplier.setCreatedAt(LocalDateTime.now());
        supplierMapper.insert(supplier);
        if (supplier.getSupplierId() == null) {
            throw new IllegalStateException("基地创建失败，请稍后重试");
        }

        String autoUsername = buildAccountUsername(supplier.getSupplierId());
        long existingCount = userMapper.selectCount(new QueryWrapper<User>().eq("username", autoUsername));
        if (existingCount > 0) {
            throw new IllegalStateException("自动生成账号失败，请稍后重试");
        }
        String defaultPassword = generateRandomPassword();

        User user = new User();
        user.setUsername(autoUsername);
        user.setPasswordHash(MD5Utils.encrypt(defaultPassword));
        user.setRole("enterprise");
        user.setFullName(supplier.getSupplierName());
        user.setContactInfo(supplier.getContactPhone());
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);

        enrichSupplier(supplier);

        Map<String, Object> data = new HashMap<>();
        data.put("supplier", supplier);
        data.put("username", autoUsername);
        data.put("password", defaultPassword);
        data.put("userId", user.getUserId());
        return data;
    }

    public List<Supplier> listWithAccountInfo() {
        List<Supplier> suppliers = supplierMapper.selectList(null);
        for (Supplier supplier : suppliers) {
            enrichSupplier(supplier);
        }
        return suppliers;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean updateSupplierAndAccount(Supplier supplier) {
        Supplier dbSupplier = supplierMapper.selectById(supplier.getSupplierId());
        if (dbSupplier == null) {
            return false;
        }

        User linkedAccount = findLinkedEnterpriseAccount(dbSupplier);
        supplierMapper.updateById(supplier);

        if (linkedAccount != null) {
            linkedAccount.setFullName(supplier.getSupplierName());
            linkedAccount.setContactInfo(supplier.getContactPhone());
            linkedAccount.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(linkedAccount);
        }
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> resetSupplierAccountPassword(Integer supplierId) {
        Supplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) {
            throw new IllegalArgumentException("基地不存在");
        }

        User linkedAccount = findLinkedEnterpriseAccount(supplier);
        if (linkedAccount == null) {
            throw new IllegalArgumentException("该基地尚未关联企业账号，无法重置密码");
        }

        String newPassword = generateRandomPassword();
        linkedAccount.setPasswordHash(MD5Utils.encrypt(newPassword));
        linkedAccount.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(linkedAccount);
        AuthInterceptor.removeSessionsByUserId(linkedAccount.getUserId());

        Map<String, Object> data = new HashMap<>();
        data.put("supplierId", supplierId);
        data.put("username", linkedAccount.getUsername());
        data.put("password", newPassword);
        data.put("enabled", normalizeEnabled(linkedAccount));
        return data;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateSupplierAccountStatus(Integer supplierId, Boolean enabled) {
        if (enabled == null) {
            throw new IllegalArgumentException("请指定账号状态");
        }

        Supplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) {
            throw new IllegalArgumentException("基地不存在");
        }

        User linkedAccount = findLinkedEnterpriseAccount(supplier);
        if (linkedAccount == null) {
            throw new IllegalArgumentException("该基地尚未关联企业账号，无法修改状态");
        }

        boolean currentEnabled = normalizeEnabled(linkedAccount);
        if (currentEnabled == enabled) {
            Map<String, Object> sameResult = new HashMap<>();
            sameResult.put("enabled", enabled);
            sameResult.put("message", enabled ? "该基地账号已经处于启用状态" : "该基地账号已经处于禁用状态");
            return sameResult;
        }

        linkedAccount.setEnabled(enabled);
        linkedAccount.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(linkedAccount);
        if (!enabled) {
            AuthInterceptor.removeSessionsByUserId(linkedAccount.getUserId());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("enabled", enabled);
        result.put("message", enabled ? "基地账号已启用" : "基地账号已禁用，并已强制退出当前登录");
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deleteSupplierCascade(Integer supplierId) {
        Map<String, Object> result = new HashMap<>();
        Supplier supplier = supplierMapper.selectById(supplierId);
        if (supplier == null) {
            result.put("deleted", false);
            result.put("message", "基地不存在");
            return result;
        }

        User linkedAccount = findLinkedEnterpriseAccount(supplier);
        if (linkedAccount != null) {
            long batchCount = productBatchMapper.selectCount(
                    new QueryWrapper<ProductBatch>().eq("manufacturer_id", linkedAccount.getUserId()));
            if (batchCount > 0) {
                result.put("deleted", false);
                result.put("message", "该基地账号下仍有关联批次，请先删除或转移批次后再删除基地");
                result.put("batchCount", batchCount);
                return result;
            }
        }

        supplierMapper.deleteById(supplierId);

        if (linkedAccount != null) {
            userMapper.deleteById(linkedAccount.getUserId());
            AuthInterceptor.removeSessionsByUserId(linkedAccount.getUserId());
        }

        result.put("deleted", true);
        result.put("message", linkedAccount != null ? "基地及其企业账号已删除" : "基地已删除");
        return result;
    }

    public void enrichSupplier(Supplier supplier) {
        User linkedAccount = findLinkedEnterpriseAccount(supplier);
        if (linkedAccount == null) {
            supplier.setAccountUserId(null);
            supplier.setAccountUsername(null);
            supplier.setAccountEnabled(null);
            supplier.setRelatedBatchCount(0);
            return;
        }

        supplier.setAccountUserId(linkedAccount.getUserId());
        supplier.setAccountUsername(linkedAccount.getUsername());
        supplier.setAccountEnabled(normalizeEnabled(linkedAccount));
        Long batchCount = productBatchMapper.selectCount(
                new QueryWrapper<ProductBatch>().eq("manufacturer_id", linkedAccount.getUserId()));
        supplier.setRelatedBatchCount(batchCount == null ? 0 : batchCount.intValue());
    }

    private String generateRandomPassword() {
        return "Ff" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private User findLinkedEnterpriseAccount(Supplier supplier) {
        if (supplier == null || supplier.getSupplierId() == null) {
            return null;
        }

        QueryWrapper<User> byUsername = new QueryWrapper<>();
        byUsername.eq("role", "enterprise")
                .eq("username", buildAccountUsername(supplier.getSupplierId()))
                .last("LIMIT 1");
        User linked = userMapper.selectOne(byUsername);
        if (linked != null) {
            return linked;
        }

        if (isBlank(supplier.getSupplierName()) || isBlank(supplier.getContactPhone())) {
            return null;
        }

        QueryWrapper<User> fallback = new QueryWrapper<>();
        fallback.eq("role", "enterprise")
                .eq("full_name", supplier.getSupplierName())
                .eq("contact_info", supplier.getContactPhone());
        List<User> users = userMapper.selectList(fallback);
        return users.size() == 1 ? users.get(0) : null;
    }

    private String buildAccountUsername(Integer supplierId) {
        return "biz_" + supplierId;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean normalizeEnabled(User user) {
        return user != null && !Boolean.FALSE.equals(user.getEnabled());
    }
}
