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