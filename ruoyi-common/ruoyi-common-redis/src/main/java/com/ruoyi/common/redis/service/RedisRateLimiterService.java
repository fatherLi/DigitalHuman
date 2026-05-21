package com.ruoyi.common.redis.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 分布式限流服务（企业级滑动窗口/令牌桶限流实现）
 * 
 * @author ruoyi
 */
@Service
public class RedisRateLimiterService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 滑动窗口限流 Lua 脚本
     * 使用 ZSET 实现，记录每一次请求的时间戳
     * KEYS[1] = key (限流目标, 如 IP 或手机号)
     * ARGV[1] = 窗口大小 (毫秒)
     * ARGV[2] = 允许的最大请求数
     * ARGV[3] = 当前时间戳
     */
    private static final String SLIDING_WINDOW_LUA = 
        "local key = KEYS[1] " +
        "local window = tonumber(ARGV[1]) " +
        "local limit = tonumber(ARGV[2]) " +
        "local now = tonumber(ARGV[3]) " +
        "local minTime = now - window " +
        "redis.call('ZREMRANGEBYSCORE', key, '-inf', minTime) " +
        "local count = redis.call('ZCARD', key) " +
        "if count >= limit then " +
        "   return 0 " +
        "end " +
        "redis.call('ZADD', key, now, now) " +
        "redis.call('PEXPIRE', key, window) " +
        "return 1 ";

    private final DefaultRedisScript<Long> slidingWindowScript;

    public RedisRateLimiterService() {
        slidingWindowScript = new DefaultRedisScript<>();
        slidingWindowScript.setScriptText(SLIDING_WINDOW_LUA);
        slidingWindowScript.setResultType(Long.class);
    }

    /**
     * 滑动窗口限流
     *
     * @param key        限流标识 (如: sms:limit:13800138000:min)
     * @param timeWindow 时间窗口时长(毫秒)
     * @param limit      窗口内允许的最大请求数
     * @return true: 允许通过, false: 触发限流
     */
    public boolean isAllowed(String key, long timeWindow, int limit) {
        long now = System.currentTimeMillis();
        Long result = stringRedisTemplate.execute(
                slidingWindowScript,
                Collections.singletonList(key),
                String.valueOf(timeWindow),
                String.valueOf(limit),
                String.valueOf(now)
        );
        return result != null && result == 1L;
    }
}
