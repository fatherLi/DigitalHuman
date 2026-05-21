package com.ruoyi.digitalman.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.ruoyi.digitalman.service.LlmStreamingService;
import com.ruoyi.common.security.utils.SecurityUtils;

import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 虚拟数字人交互控制层
 * 支持 WebFlux / SSE 双通道流式架构
 */
@RestController
@RequestMapping("/chat")
public class DigitalHumanChatController {

    @Autowired
    private LlmStreamingService llmStreamingService;

    /**
     * 发送提问，并流式获取回答 (SSE)
     * 同时底层会通过 RabbitMQ 异步触发数字人音视频渲染
     *
     * @param question 用户提问
     * @return Flux<ServerSentEvent<String>> 服务器推送事件流
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestParam("question") String question) {
        Long userId = SecurityUtils.getUserId();
        
        // 异步调用大模型及数字人任务分发，返回纯响应式的 Flux
        return llmStreamingService.streamChatAndDispatch(userId, question)
                .map(token -> ServerSentEvent.<String>builder()
                        .data(token)
                        .build());
    }
}
