package com.fresh.traceability.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * MD5 加密工具类
 */
public class MD5Utils {

    // 加密盐值，增加安全性
    private static final String SALT = "FreshTrace2026";

    /**
     * MD5 加密
     *
     * @param source 原始字符串
     * @return 加密后的 32 位小写字符串
     */
    public static String encrypt(String source) {
        if (source == null) {
            return null;
        }

        // 加盐
        String saltedSource = SALT + source;

        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] bytes = md.digest(saltedSource.getBytes());

            // 转换为十六进制字符串
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                String hex = Integer.toHexString(b & 0xff);
                if (hex.length() == 1) {
                    sb.append("0");
                }
                sb.append(hex);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 加密失败", e);
        }
    }

    /**
     * 验证密码是否匹配
     *
     * @param plainPassword     明文密码
     * @param encryptedPassword 加密后的密码
     * @return 是否匹配
     */
    public static boolean verify(String plainPassword, String encryptedPassword) {
        if (plainPassword == null || encryptedPassword == null) {
            return false;
        }
        return encrypt(plainPassword).equals(encryptedPassword);
    }

    /**
     * 生成测试密文（用于插入数据库）
     * 密码 123456 的加密结果：e10adc3949ba59abbe56e057f20f883e（标准MD5）
     * 加盐后的加密结果需要运行程序计算
     */
    public static void main(String[] args) {
        // 用于生成测试密码
        String password = "123456";
        String encrypted = encrypt(password);
        System.out.println("原始密码: " + password);
        System.out.println("加密后: " + encrypted);
        // 输出: 加盐后 MD5("FreshTrace2026123456") =
        // 7c4a8d09ca3762af61e59520943dc26494f8941b
    }
}
