package com.fresh.traceability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fresh.traceability.entity.QrCode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

@Mapper
public interface QrCodeMapper extends BaseMapper<QrCode> {

    /**
     * 按状态分组统计二维码数量
     */
    @Select("SELECT CASE WHEN status IS NULL OR status = '' OR status = 'Sold' THEN 'Active' ELSE status END AS status, COUNT(*) AS cnt " +
            "FROM qr_codes GROUP BY CASE WHEN status IS NULL OR status = '' OR status = 'Sold' THEN 'Active' ELSE status END")
    List<Map<String, Object>> countByStatus();

    /**
     * 统计某个企业二维码状态分布
     */
    @Select("SELECT CASE WHEN q.status IS NULL OR q.status = '' OR q.status = 'Sold' THEN 'Active' ELSE q.status END AS status, COUNT(*) AS cnt " +
            "FROM qr_codes q JOIN product_batches p ON q.batch_id = p.batch_id " +
            "WHERE p.manufacturer_id = #{manufacturerId} " +
            "GROUP BY CASE WHEN q.status IS NULL OR q.status = '' OR q.status = 'Sold' THEN 'Active' ELSE q.status END")
    List<Map<String, Object>> countByStatusByManufacturer(Integer manufacturerId);

    @Select("SELECT * FROM qr_codes WHERE unique_code = #{uniqueCode} LIMIT 1")
    QrCode selectByUniqueCode(String uniqueCode);

    @Update("UPDATE qr_codes SET scan_count = COALESCE(scan_count, 0) + 1, " +
            "first_scan_time = CASE WHEN first_scan_time IS NULL THEN NOW() ELSE first_scan_time END " +
            "WHERE unique_code = #{uniqueCode}")
    int increaseScanCount(String uniqueCode);

    @Update("UPDATE qr_codes SET status = 'Expired' " +
            "WHERE qr_id = #{qrId} AND (status IS NULL OR status <> 'Recalled')")
    int markExpiredIfEligible(Integer qrId);
}
