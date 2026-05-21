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
     * 流式对话与任务分发 (完全响应式)
     */
    public Flux<String> streamChatAndDispatch(Long userId, String question) {
        
        // 1. 分布式事务入口：扣减代币
        try {
            transactionService.deductUserToken(userId, 1L); // 每次对话扣除1个代币
        } catch (Exception e) {
            log.error("用户代币扣减失败，事务回滚", e);
            return Flux.just("[ERROR] 账户代币不足或扣减失败");
        }

        // 2. 动态 LLM 上下文管理
        String contextPrompt = contextCacheService.buildContextPrompt(userId, question);
        log.info("构建的大模型上下文：{}", contextPrompt);

        // 3. 模拟异步流式调用大模型
        String mockLlmAnswer = "您好！我是您的虚拟数字人助手。基于您提供的企业级微服务重构方案，我已经成功启动。接下来将为您呈现极低延迟的语音播报与口型渲染同步效果！";
        
        // 保存完整回答的 StringBuilder
        StringBuilder fullAnswer = new StringBuilder();

        return Flux.fromArray(mockLlmAnswer.split(""))
                .delayElements(Duration.ofMillis(50))
                .doOnNext(token -> {
                    // 记录拼接最终答案
                    fullAnswer.append(token);
                })
                .doOnComplete(() -> {
                    log.info("大模型回答结束，保存上下文并触发数字人渲染");
                    // 4. 将最终回答存入 Redis 滑窗会话缓存
                    contextCacheService.saveContext(userId, question, fullAnswer.toString());

                    // 5. 触发亮点二：RabbitMQ 异步削峰
                    transactionService.sendDigitalHumanTask(userId, fullAnswer.toString());
                })
                .doOnError(error -> {
                    log.error("大模型流式调用异常", error);
                });
    }
}
