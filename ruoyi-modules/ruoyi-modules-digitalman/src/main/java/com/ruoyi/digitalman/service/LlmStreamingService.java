package com.ruoyi.digitalman.service;

import com.ruoyi.digitalman.domain.DigitalHumanTask;
import com.ruoyi.digitalman.mq.DigitalHumanTaskProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.function.Consumer;

/**
 * 大模型流式处理与异步任务调度服务
 */
@Service
public class LlmStreamingService {

    private static final Logger log = LoggerFactory.getLogger(LlmStreamingService.class);

    @Autowired
    private DigitalHumanTaskProducer taskProducer;

    /**
     * @param prompt   用户提问
     * @param userId   用户ID
     * @param callback 回调函数，负责将生成的字实时推送给 Controller 的 Writer
     */
    public void processStreaming(String prompt, Long userId, Consumer<String> callback) {
        log.info("开始处理用户 {} 的流式对话请求: {}", userId, prompt);

        // 模拟调用大模型吐字逻辑
        String[] tokens = {"你好", "，", "我", "是", "基于", "若依", "构建的", "数字人", "助手", "。"};

        for (String token : tokens) {
            // 1. 回调给 Controller 的 PrintWriter，实现前端流式显示
            callback.accept(token);

            // 2. 将此片段异步推送给 RabbitMQ，交给后台 GPU 引擎处理音视频渲染
            // mouthShape: "auto" 表示后端自动识别嘴型；style: "formal" 表示播报风格
            DigitalHumanTask task = new DigitalHumanTask(userId, token, "auto", "formal");
            taskProducer.sendTask(task);

            try {
                // 模拟大模型生成每个 Token 的耗时
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("流式生成任务被中断", e);
            }
        }
        log.info("用户 {} 对话处理完成", userId);
    }
}