package com.nano.oj.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 全局配置
 * 解决 Long 类型传给前端精度丢失问题
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return jacksonObjectMapperBuilder -> {
            // 创建一个模块，专门处理 Long 类型的序列化
            SimpleModule simpleModule = new SimpleModule();
            // 将 Long 类型序列化为 String
            simpleModule.addSerializer(Long.class, ToStringSerializer.instance);
            // 将 long 基本类型序列化为 String
            simpleModule.addSerializer(Long.TYPE, ToStringSerializer.instance);

            // 注册模块
            jacksonObjectMapperBuilder.modules(simpleModule);
        };
    }
}