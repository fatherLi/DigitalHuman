package com.ruoyi.digitalman.mq;

import com.ruoyi.digitalman.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.UUID;

/**
 * 数字人任务生产者
 * 支持 Confirm 和 Return 机制，确保消息不丢失
 */
@Service
public class DigitalHumanTaskProducer implements RabbitTemplate.ConfirmCallback, RabbitTemplate.ReturnsCallback {

    private static final Logger log = LoggerFactory.getLogger(DigitalHumanTaskProducer.class);

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostConstruct
    public void init() {
        rabbitTemplate.setConfirmCallback(this);
        rabbitTemplate.setReturnsCallback(this);
    }

    public void sendTask(Long userId, String text) {
        String msgId = UUID.randomUUID().toString();
        String messagePayload = String.format("{\"userId\":%d, \"text\":\"%s\", \"msgId\":\"%s\"}", userId, text, msgId);
        
        CorrelationData correlationData = new CorrelationData(msgId);
        log.info("准备发送数字人渲染任务，消息ID: {}, 用户ID: {}", msgId, userId);
        
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.DIGITALMAN_EXCHANGE,
                RabbitMQConfig.TASK_ROUTING_KEY,
                messagePayload,
                correlationData
        );
    }

    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (ack) {
            log.info("消息已成功投递到 Exchange, 消息ID: {}", correlationData != null ? correlationData.getId() : "");
        } else {
            log.error("消息投递到 Exchange 失败, 消息ID: {}, 原因: {}", correlationData != null ? correlationData.getId() : "", cause);
            // 这里可以进行失败重试记录或补偿机制
        }
    }

    @Override
    public void returnedMessage(org.springframework.amqp.core.ReturnedMessage returned) {
        log.error("消息被 Exchange 退回！路由键不匹配或队列不存在。内容: {}, replyCode: {}, replyText: {}, exchange: {}, routingKey: {}",
                new String(returned.getMessage().getBody()),
                returned.getReplyCode(),
                returned.getReplyText(),
                returned.getExchange(),
                returned.getRoutingKey());
    }
}
