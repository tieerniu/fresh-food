package com.fresh.traceability.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fresh.traceability.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
