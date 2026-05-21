package com.ruoyi.digitalman.controller;

import com.ruoyi.digitalman.service.LlmStreamingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import com.ruoyi.common.security.utils.SecurityUtils;

@RestController
@RequestMapping("/digitalman")
public class DigitalHumanChatController {

    @Autowired
    private LlmStreamingService llmStreamingService;

    /**
     * 【项目经验 第 5 个月：端到端联调、传输优化与性能压测】
     * [第一步：流量入口] 接收前端 SSE 连接请求
     *
     * 亮点一：企业级大模型低延时流式双通道接口
     * 必须指定 produces = MediaType.TEXT_EVENT_STREAM_VALUE，保证边生成边推送
     *
     * 下一步去哪：进入 llmStreamingService.streamChatAndDispatch(userId, question) 进行流式处理
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestParam("question") String question) {
        Long userId = SecurityUtils.getUserId();
        
        // 异步调用大模型及数字人任务分发，返回纯响应式的 Flux
        return llmStreamingService.streamChatAndDispatch(userId, question)
                .map(token -> ServerSentEvent.<String>builder()
                        .data(token)
                        .build());
    }
}