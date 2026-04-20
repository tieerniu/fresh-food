package com.fresh.traceability;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.fresh.traceability.mapper") // 扫描 Mapper 接口
public class FreshFoodApplication {
    public static void main(String[] args) {
        SpringApplication.run(FreshFoodApplication.class, args);
    }
}