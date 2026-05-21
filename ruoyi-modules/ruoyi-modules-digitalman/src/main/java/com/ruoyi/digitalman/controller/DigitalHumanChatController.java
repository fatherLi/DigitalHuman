package com.ruoyi.digitalman.controller;

import com.ruoyi.digitalman.service.LlmStreamingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

@RestController
@RequestMapping("/digitalman")
public class DigitalHumanChatController {

    @Autowired
    private LlmStreamingService llmStreamingService;

    @GetMapping("/chat/stream")
    public void streamChat(@RequestParam String prompt, @RequestParam Long userId, HttpServletResponse response) throws IOException {
        // 设置响应头，告诉浏览器这是流式输出
        response.setContentType("text/event-stream;charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");

        PrintWriter writer = response.getWriter();

        // 调用 Service，Service 现在返回 List 或直接循环回调
        // 这里假设 Service 返回一个 Iterator 或者直接在内部做回调
        llmStreamingService.processStreaming(prompt, userId, (token) -> {
            try {
                writer.write("data: " + token + "\n\n");
                writer.flush(); // 强制推送到前端
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}