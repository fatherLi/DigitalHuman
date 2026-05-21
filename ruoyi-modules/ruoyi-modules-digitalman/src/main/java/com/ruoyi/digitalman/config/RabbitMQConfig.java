package com.ruoyi.digitalman.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 亮点二：RabbitMQ 配置类
 * 构建高可用数字人任务调度缓冲池及死信队列削峰架构
 */
@Configuration
public class RabbitMQConfig {

    // 正常业务队列和交换机
    public static final String DIGITALMAN_EXCHANGE = "digitalman.exchange";
    public static final String TASK_QUEUE = "digitalman.task.queue";
    public static final String TASK_ROUTING_KEY = "digitalman.task.routing";

    // 死信队列和交换机 (用于处理消费者崩溃或超时未处理的任务)
    public static final String DLX_EXCHANGE = "digitalman.dlx.exchange";
    public static final String DLX_QUEUE = "digitalman.dlx.queue";
    public static final String DLX_ROUTING_KEY = "digitalman.dlx.routing";

    @Bean
    public DirectExchange digitalmanExchange() {
        return new DirectExchange(DIGITALMAN_EXCHANGE);
    }

    @Bean
    public Queue taskQueue() {
        return QueueBuilder.durable(TASK_QUEUE)
                // 配置死信交换机
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                // 配置死信路由键
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY)
                // 设置消息在队列中的过期时间 (例如 5 分钟，防止任务无限制堆积)
                .withArgument("x-message-ttl", 300000)
                .build();
    }

    @Bean
    public Binding taskBinding(Queue taskQueue, DirectExchange digitalmanExchange) {
        return BindingBuilder.bind(taskQueue).to(digitalmanExchange).with(TASK_ROUTING_KEY);
    }

    // --- 死信队列配置 ---
    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(DLX_QUEUE).build();
    }

    @Bean
    public Binding dlxBinding(Queue dlxQueue, DirectExchange dlxExchange) {
        return BindingBuilder.bind(dlxQueue).to(dlxExchange).with(DLX_ROUTING_KEY);
    }
}
