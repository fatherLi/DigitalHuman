package com.ruoyi.digitalman.mq;

import com.rabbitmq.client.Channel;
import com.ruoyi.digitalman.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * 数字人任务消费者 (削峰核心)
 * 配置了手动 ACK 机制和消费速率限制 (prefetch count)，根据 GPU 算力动态处理渲染任务
 */
@Service
public class DigitalHumanTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(DigitalHumanTaskConsumer.class);

    /**
     * concurrency="1-5" 动态伸缩消费者数量
     * ackMode="MANUAL" 手动确认，确保任务成功执行后才从队列中删除
     */
    @RabbitListener(
            queues = RabbitMQConfig.TASK_QUEUE,
            concurrency = "1-3",
            ackMode = "MANUAL"
    )
    public void receiveTask(String payload, Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            log.info("【数字人渲染消费者】接收到任务消息: {}", payload);
            
            // 模拟高耗时的数字人音视频渲染调用 (假设耗时 3-5 秒)
            simulateGpuRenderingTask();
            
            // 渲染成功，手动确认 (ACK)
            log.info("【数字人渲染消费者】渲染成功，确认消息");
            channel.basicAck(deliveryTag, false);
            
        } catch (Exception e) {
            log.error("【数字人渲染消费者】渲染失败，准备进行错误处理", e);
            // 发生异常时，如果是由于暂时性的网络问题，可以 requeue=true
            // 这里为了演示削峰保护和死信队列，我们选择丢弃消息 (requeue=false)，使其进入死信队列进行补偿
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private void simulateGpuRenderingTask() throws InterruptedException {
        // 模拟调用第三方数字人引擎 (例如驱动 Live2D 或 3D 骨骼并进行声音合成 TTS)
        Thread.sleep(3000 + (long)(Math.random() * 2000));
    }
}
