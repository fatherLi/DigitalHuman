package com.ruoyi.digitalman.service;

import com.ruoyi.digitalman.domain.DigitalHumanTask;
import com.ruoyi.digitalman.mq.DigitalHumanTaskProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 大模型流式处理与异步任务调度服务
 */
@Service
public class LlmStreamingService {

    private static final Logger log = LoggerFactory.getLogger(LlmStreamingService.class);

    @Autowired
    private DigitalHumanTaskProducer taskProducer;

    // 全局并发限流器：最多允许 20 个并发流式请求。
    // 纯响应式非阻塞方案：超过 20 个并发时直接 fast-fail (快速失败)，保护下游数字人资源，
    // 前端收到报错后可提示用户重试，完美保持 WebFlux 无阻塞优势。
    private final AtomicInteger activeStreams = new AtomicInteger(0);

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
        return Flux.defer(() -> {
            // 非阻塞限流：如果当前并发数 >= 20，直接返回错误，避免积压和前端“空等”
            if (activeStreams.incrementAndGet() > 20) {
                activeStreams.decrementAndGet();
                log.warn("并发数达上限，拒绝用户 {} 的请求", userId);
                return Flux.error(new RuntimeException("当前使用人数过多，系统繁忙，请稍后再试"));
            }
            
            log.info("开始处理用户 {} 的请求，当前并发数: {}", userId, activeStreams.get());

            // 模拟调用大模型吐字逻辑
            String[] tokens = {"你好", "，", "我", "是", "基于", "若依", "构建的", "数字人", "助手", "。"};

            return Flux.fromArray(tokens)
                    .delayElements(Duration.ofMillis(200)) // 模拟大模型生成每个 Token 的耗时
                    // 使用 concatMap 结合纯响应式的 sendTaskReactive
                    // 不阻塞任何 Netty 线程，保持 WebFlux 极致性能，且若 MQ 报错必定阻断发字！
                    .concatMap(token -> {
                        DigitalHumanTask task = new DigitalHumanTask(userId, token, "auto", "formal");
                        return taskProducer.sendTaskReactive(task).thenReturn(token);
                    })
                    .doOnComplete(() -> log.info("用户 {} 对话流式处理完成", userId))
                    .doFinally(signalType -> {
                        int current = activeStreams.decrementAndGet();
                        log.info("用户 {} 对话结束（状态: {}），当前并发数: {}", userId, signalType, current);
                    });
        });
    }
}