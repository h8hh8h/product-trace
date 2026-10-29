package com.trace.product;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.trace.product.mapper")
@EnableScheduling
public class ProductTraceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductTraceApplication.class, args);
    }
}
