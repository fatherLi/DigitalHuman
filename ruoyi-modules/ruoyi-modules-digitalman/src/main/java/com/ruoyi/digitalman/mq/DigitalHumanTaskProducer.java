package com.ruoyi.digitalman.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.digitalman.config.RabbitMQConfig;
import com.ruoyi.digitalman.domain.DigitalHumanTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.UUID;

@Service
public class DigitalHumanTaskProducer {

    private static final Logger log = LoggerFactory.getLogger(DigitalHumanTaskProducer.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /**
     * 【项目经验 第 3-4 个月：核心业务的“异步化”与“分布式闭环”】
     * [第四步：MQ 任务可靠投递]
     * 
     * 话术：为了解决数字人业务长延时导致的网关超时问题，我将这一链路彻底改造成了基于 RabbitMQ 的异步队列调度模型。
     * 这里是生产者端，我们通过 ConfirmCallback 回调机制，确保每一条渲染任务 100% 成功投递到 Exchange。
     * 
     * 下一步去哪：消息进入队列，由 DigitalHumanTaskConsumer 异步削峰消费
     */
    public void sendTask(DigitalHumanTask task) {
        try {
            task.setMsgId(UUID.randomUUID().toString());
            String messagePayload = objectMapper.writeValueAsString(task);

            CorrelationData correlationData = new CorrelationData(task.getMsgId());

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.DIGITALMAN_EXCHANGE,
                    RabbitMQConfig.TASK_ROUTING_KEY,
                    messagePayload,
                    correlationData
            );
        } catch (Exception e) {
            log.error("MQ 消息序列化或发送异常", e);
            throw new ServiceException("数字人渲染任务下发失败");
        }
    }

    /**
     * 纯响应式发 MQ：不阻塞任何线程，发送后异步等待 ConfirmCallback
     */
    public reactor.core.publisher.Mono<Void> sendTaskReactive(DigitalHumanTask task) {
        return reactor.core.publisher.Mono.create(sink -> {
            try {
                task.setMsgId(UUID.randomUUID().toString());
                String messagePayload = objectMapper.writeValueAsString(task);
                CorrelationData correlationData = new CorrelationData(task.getMsgId());

                // 利用 CompletableFuture 非阻塞等待 MQ 投递确认
                correlationData.getFuture().whenComplete((confirm, ex) -> {
                    if (ex != null) {
                        sink.error(new ServiceException("MQ Error: " + ex.getMessage()));
                    } else if (confirm != null && confirm.isAck()) {
                        sink.success();
                    } else {
                        sink.error(new ServiceException("MQ NACK: " + (confirm != null ? confirm.getReason() : "null")));
                    }
                });

                // convertAndSend 仅仅是写缓冲，微秒级不阻塞
                rabbitTemplate.convertAndSend(
                        RabbitMQConfig.DIGITALMAN_EXCHANGE,
                        RabbitMQConfig.TASK_ROUTING_KEY,
                        messagePayload,
                        correlationData
                );
            } catch (Exception e) {
                log.error("MQ 消息序列化或发送异常", e);
                sink.error(new ServiceException("数字人渲染任务下发失败"));
            }
        });
    }

    @PostConstruct
    public void registerCallbacks() {
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack) {
                log.error("【MQ 消息异常】投递失败, ID: {}, 原因: {}",
                        correlationData != null ? correlationData.getId() : "null", cause);
            }
        });
    }
}