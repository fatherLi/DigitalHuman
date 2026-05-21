package com.ruoyi.digitalman.service;

import com.ruoyi.digitalman.domain.DigitalHumanTask;
import com.ruoyi.digitalman.mq.DigitalHumanTaskProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import java.time.Duration;

/**
 * 大模型流式处理与异步任务调度服务
 */
@Service
public class LlmStreamingService {

    private static final Logger log = LoggerFactory.getLogger(LlmStreamingService.class);

    @Autowired
    private DigitalHumanTaskProducer taskProducer;

    /**
     * 【项目经验 第 5 个月：端到端联调、传输优化与性能压测】
     * [第二步：大模型异步流式调用（TTFT 优化）]
     *
     * 话术：最后阶段，我们进行了大模型流式传输优化。我们需要配合前端使用 SSE 做流式推送，解决白屏问题。
     * 在这里我将服务彻底改造成无阻塞的 Flux，保证大模型生成和前端接收是真正的“边生成边推送”。
     *
     * 下一步去哪：数据流将被拆分，一部分通过 Flux 返回前端，另一部分发往 MQ：DigitalHumanTaskProducer
     */
    public Flux<String> streamChatAndDispatch(Long userId, String prompt) {
        log.info("开始处理用户 {} 的流式对话请求: {}", userId, prompt);

        // 模拟调用大模型吐字逻辑
        String[] tokens = {"你好", "，", "我", "是", "基于", "若依", "构建的", "数字人", "助手", "。"};

        return Flux.fromArray(tokens)
                .delayElements(Duration.ofMillis(200)) // 模拟大模型生成每个 Token 的耗时
                .doOnNext(token -> {
                    // 【项目经验 第 3-4 个月：核心业务的“异步化”与“分布式闭环”】
                    // 将此片段异步推送给 RabbitMQ，交给后台 GPU 引擎处理音视频渲染
                    // 话术：数字人播报属于“长延时、高算力”业务，如果走同步 HTTP，网关必然超时，服务必然卡死。
                    // 我将这一链路彻底改造成了基于 RabbitMQ 的异步队列调度模型。
                    DigitalHumanTask task = new DigitalHumanTask(userId, token, "auto", "formal");
                    taskProducer.sendTask(task);
                })
                .doOnComplete(() -> log.info("用户 {} 对话流式处理完成", userId));
    }
}