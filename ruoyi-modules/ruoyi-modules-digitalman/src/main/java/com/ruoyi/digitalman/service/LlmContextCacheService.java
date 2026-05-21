package com.ruoyi.digitalman.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 亮点三：大模型上下文动态管理与 Redis 缓存优化
 * 使用 Redis List 结构维护用户的多轮对话历史，实现高效的滑动窗口
 */
@Service
public class LlmContextCacheService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 滑动窗口：最大保留的历史轮数 (1轮 = Q+A, 占用两个 List 元素)
    private static final int MAX_ROUNDS = 5; 
    
    // 会话过期时间：30分钟无交互则清空上下文
    private static final long SESSION_EXPIRE_MINUTES = 30;

    private String getSessionKey(Long userId) {
        return "llm:session:context:" + userId;
    }

    /**
     * 构建包含历史上下文的 Prompt
     */
    public String buildContextPrompt(Long userId, String currentQuestion) {
        String key = getSessionKey(userId);
        
        // 从 Redis List 获取所有历史记录 (最新的在列表尾部)
        List<String> history = stringRedisTemplate.opsForList().range(key, 0, -1);
        
        StringBuilder promptBuilder = new StringBuilder();
        if (history != null && !history.isEmpty()) {
            promptBuilder.append("以下是我们的历史对话上下文：\n");
            for (String msg : history) {
                promptBuilder.append(msg).append("\n");
            }
            promptBuilder.append("基于以上上下文，请回答我的新问题：\n");
        }
        
        promptBuilder.append(currentQuestion);
        
        // 刷新过期时间
        stringRedisTemplate.expire(key, SESSION_EXPIRE_MINUTES, TimeUnit.MINUTES);
        
        return promptBuilder.toString();
    }

    /**
     * 保存一轮新的对话记录，并维持滑动窗口大小
     */
    public void saveContext(Long userId, String question, String answer) {
        String key = getSessionKey(userId);
        
        // 追加新的一轮 Q & A 到 List 尾部 (Right Push)
        stringRedisTemplate.opsForList().rightPush(key, "User: " + question);
        stringRedisTemplate.opsForList().rightPush(key, "AI: " + answer);
        
        // 获取当前 List 长度
        Long size = stringRedisTemplate.opsForList().size(key);
        
        // 维持滑动窗口：1轮占用2个元素，最大允许 MAX_ROUNDS * 2 个元素
        int maxElements = MAX_ROUNDS * 2;
        if (size != null && size > maxElements) {
            // LTRIM: 仅保留最新的 maxElements 个元素，裁剪掉最早的记录
            stringRedisTemplate.opsForList().trim(key, size - maxElements, -1);
        }
        
        // 刷新过期时间
        stringRedisTemplate.expire(key, SESSION_EXPIRE_MINUTES, TimeUnit.MINUTES);
    }
}
