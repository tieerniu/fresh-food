package com.fresh.traceability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fresh.traceability.entity.ScanLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 扫码日志 Mapper
 */
@Mapper
public interface ScanLogMapper extends BaseMapper<ScanLog> {

    /**
     * 统计今日扫码次数
     */
    @Select("SELECT COUNT(*) FROM scan_logs WHERE DATE(scan_time) = CURDATE()")
    Long countTodayScans();

    /**
     * 统计近7天每日扫码次数
     */
    @Select("SELECT DATE(scan_time) AS day, COUNT(*) AS cnt FROM scan_logs WHERE scan_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) GROUP BY DATE(scan_time) ORDER BY day")
    java.util.List<java.util.Map<String, Object>> countByDayRecent7();

    /**
     * 统计某个企业今天发行的二维码扫码次数
     */
    @Select("SELECT COUNT(*) FROM scan_logs s JOIN qr_codes q ON s.qr_code_id = q.qr_id JOIN product_batches p ON q.batch_id = p.batch_id WHERE DATE(s.scan_time) = CURDATE() AND p.manufacturer_id = #{manufacturerId}")
    Long countTodayScansByManufacturer(Integer manufacturerId);

    /**
     * 统计某个企业近7天每日扫码次数
     */
    @Select("SELECT DATE(s.scan_time) AS day, COUNT(*) AS cnt FROM scan_logs s JOIN qr_codes q ON s.qr_code_id = q.qr_id JOIN product_batches p ON q.batch_id = p.batch_id WHERE s.scan_time >= DATE_SUB(CURDATE(), INTERVAL 6 DAY) AND p.manufacturer_id = #{manufacturerId} GROUP BY DATE(s.scan_time) ORDER BY day")
    java.util.List<java.util.Map<String, Object>> countByDayRecent7ByManufacturer(Integer manufacturerId);

    @Select("SELECT COUNT(*) FROM scan_logs WHERE unique_code = #{uniqueCode} AND scan_time >= DATE_SUB(NOW(), INTERVAL 10 MINUTE)")
    Long countRecentScans(@Param("uniqueCode") String uniqueCode);

    @Select("SELECT COUNT(DISTINCT COALESCE(NULLIF(device_id, ''), CONCAT('IP:', COALESCE(ip_address, '')))) " +
            "FROM scan_logs WHERE unique_code = #{uniqueCode} AND scan_time >= DATE_SUB(NOW(), INTERVAL 10 MINUTE)")
    Long countDistinctRecentVisitors(@Param("uniqueCode") String uniqueCode);

    @Select("SELECT COUNT(DISTINCT ip_address) FROM scan_logs " +
            "WHERE unique_code = #{uniqueCode} AND ip_address IS NOT NULL AND ip_address <> '' " +
            "AND scan_time >= DATE_SUB(NOW(), INTERVAL 10 MINUTE)")
    Long countDistinctRecentIps(@Param("uniqueCode") String uniqueCode);
}
