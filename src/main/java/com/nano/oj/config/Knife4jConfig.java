package com.nano.oj.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j 接口文档配置
 * 访问地址：http://localhost:8101/api/doc.html
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Nano OJ 在线判题系统") // 文档标题
                        .version("1.0")               // 版本号
                        .description("Nano OJ 接口文档") // 描述
                        .contact(new Contact().name("Nano").email("nano@example.com"))); // 联系人信息
    }
}