package com.ruoyi.digitalman.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.Duration;

/**
 * 大模型流式服务 (WebFlux / SSE)
 * 整合：通义千问流式调用、RabbitMQ异步任务分发、LLM上下文动态缓存
 */
@Service
public class LlmStreamingService {

    private static final Logger log = LoggerFactory.getLogger(LlmStreamingService.class);

    @Autowired
    private LlmContextCacheService contextCacheService;

    @Autowired
    private DigitalHumanTransactionService transactionService;

    /**
     * 流式对话与任务分发
     */
    public void streamChatAndDispatch(Long userId, String question, SseEmitter emitter) {
        
        // 1. 分布式事务入口：扣减代币
        try {
            transactionService.deductUserToken(userId, 1L); // 每次对话扣除1个代币
        } catch (Exception e) {
            log.error("用户代币扣减失败，事务回滚", e);
            try {
                emitter.send(SseEmitter.event().data("[ERROR] 账户代币不足或扣减失败"));
                emitter.complete();
            } catch (IOException ex) {
                // ignore
            }
            return;
        }

        // 2. 动态 LLM 上下文管理：从 Redis 中读取滑动窗口历史，并将当前问题加入上下文
        String contextPrompt = contextCacheService.buildContextPrompt(userId, question);
        log.info("构建的大模型上下文：{}", contextPrompt);

        // 3. 模拟异步流式调用大模型 (实际场景使用 WebClient + 真实大模型 API)
        // 为了演示，模拟每隔 50ms 吐出一个字
        String mockLlmAnswer = "您好！我是您的虚拟数字人助手。基于您提供的企业级微服务重构方案，我已经成功启动。接下来将为您呈现极低延迟的语音播报与口型渲染同步效果！";
        
        Flux<String> llmStream = Flux.fromArray(mockLlmAnswer.split(""))
                .delayElements(Duration.ofMillis(50));

        StringBuilder fullAnswer = new StringBuilder();

        // 订阅大模型流式结果
        llmStream.subscribe(
            token -> {
                try {
                    // 3.1 实时推送给前端，实现毫秒级 TTFT
                    emitter.send(SseEmitter.event().data(token));
                    fullAnswer.append(token);
                } catch (IOException e) {
                    log.error("SSE 推送异常", e);
                }
            },
            error -> {
                log.error("大模型流式调用异常", error);
                emitter.completeWithError(error);
            },
            () -> {
                // 3.2 大模型回答结束
                try {
                    emitter.send(SseEmitter.event().data("[DONE]"));
                    emitter.complete();
                } catch (IOException e) {
                    log.error("SSE 完成信号推送异常", e);
                }

                // 4. 将最终回答存入 Redis 滑窗会话缓存
                contextCacheService.saveContext(userId, question, fullAnswer.toString());

                // 5. 触发亮点二：RabbitMQ 异步削峰 (此处发送完整句子或分句进行数字人音视频渲染排队)
                transactionService.sendDigitalHumanTask(userId, fullAnswer.toString());
            }
        );
    }
}
