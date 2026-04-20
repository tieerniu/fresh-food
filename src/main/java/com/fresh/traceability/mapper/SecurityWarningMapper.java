package com.fresh.traceability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fresh.traceability.entity.SecurityWarning;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface SecurityWarningMapper extends BaseMapper<SecurityWarning> {

    @Insert("INSERT INTO security_warnings (unique_code, warning_type, warning_content, status, created_at) " +
            "SELECT #{uniqueCode}, 'FrequentScan', #{warningContent}, 'Pending', NOW() " +
            "FROM DUAL WHERE NOT EXISTS (" +
            "SELECT 1 FROM security_warnings WHERE unique_code = #{uniqueCode} AND status IN ('Pending', '待处理', 'InInspection', '待质检'))")
    int insertPendingWarningIfAbsent(String uniqueCode, String warningContent);

    @Select("SELECT COUNT(1) FROM security_warnings WHERE unique_code = #{uniqueCode} AND status IN ('Pending', '待处理', 'InInspection', '待质检')")
    long countPendingByUniqueCode(String uniqueCode);

    @Select("SELECT * FROM security_warnings " +
            "WHERE unique_code = #{uniqueCode} AND status IN ('Resolved', 'Ignored', '已处理', '已忽略') " +
            "ORDER BY COALESCE(handled_at, created_at) DESC LIMIT 1")
    SecurityWarning selectLatestHandledByUniqueCode(String uniqueCode);
}
