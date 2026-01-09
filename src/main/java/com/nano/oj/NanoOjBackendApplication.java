package com.nano.oj;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.nano.oj.mapper")
@EnableScheduling
public class NanoOjBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(NanoOjBackendApplication.class, args);
    }

}
