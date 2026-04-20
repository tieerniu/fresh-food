package com.fresh.traceability.util;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.util.DigestUtils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 二维码工具类
 * 提供二维码生成和防伪加密功能
 */
public class QrCodeUtil {

    /**
     * 生成二维码图片的 Base64 字符串
     *
     * @param content 二维码内容（如：唯一码 UUID）
     * @return Base64 编码的 PNG 图片字符串（不带 data:image/png;base64, 前缀）
     */
    public static String generateQrCodeImage(String content) {
        try {
            // 使用 ZXing 生成二维码
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(content, BarcodeFormat.QR_CODE, 300, 300);

            // 将二维码写入字节流
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            byte[] pngData = pngOutputStream.toByteArray();

            // 转换为 Base64 字符串
            return Base64.getEncoder().encodeToString(pngData);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 生成防伪加密串（模拟）
     *
     * @param batchId 批次ID
     * @param secret  密钥
     * @return MD5 加密后的字符串（大写）
     */
    public static String encryptContent(String batchId, String secret) {
        // 简单模拟：MD5(batchId + secret)
        String raw = batchId + secret;
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8)).toUpperCase();
    }

    /**
     * 生成二维码内容（带域名的完整 URL）
     *
     * @param uniqueCode 唯一码
     * @return 二维码内容字符串
     */
    public static String generateQrContent(String uniqueCode) {
        return "/?code=" + uniqueCode;
    }
}
