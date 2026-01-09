package com.nano.oj.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqConfig {

    // 定义名字，保持统一
    public static final String JUDGE_EXCHANGE = "oj_judge_exchange";
    public static final String JUDGE_QUEUE = "oj_judge_queue";
    public static final String JUDGE_ROUTING_KEY = "oj_judge_routing_key";

    @Bean
    public Queue judgeQueue() {
        // true 表示持久化，重启 MQ 队列还在
        return new Queue(JUDGE_QUEUE, true);
    }

    @Bean
    public DirectExchange judgeExchange() {
        return new DirectExchange(JUDGE_EXCHANGE);
    }

    @Bean
    public Binding judgeBinding() {
        return BindingBuilder.bind(judgeQueue()).to(judgeExchange()).with(JUDGE_ROUTING_KEY);
    }
}