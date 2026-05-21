package com.ruoyi.auth.service;

import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.core.constant.CacheConstants;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.redis.service.RedisRateLimiterService;
import com.ruoyi.common.redis.service.RedisService;

/**
 * 企业级短信发送服务
 * 包含：Redisson分布式锁、Redis滑动窗口限流防刷
 */
@Service
public class SmsService {
    private static final Logger log = LoggerFactory.getLogger(SmsService.class);

    @Autowired
    private RedisService redisService;

    @SuppressWarnings("SpringJavaInjectionPointsAutowiringInspection")
    @Autowired
    private RedisRateLimiterService rateLimiterService;

    @Autowired
    private RedissonClient redissonClient;

    /**
     * 发送登录短信验证码
     * 
     * @param phonenumber 手机号
     * @param ip 请求IP
     */
    public void sendLoginSms(String phonenumber, String ip) {
        // 1. IP 和手机号防刷限流（秒级/分钟级/天级）
        checkRateLimit(phonenumber, ip);

        // 2. Redisson 分布式锁，防止高并发下同一个手机号同时多次请求生成验证码
        String lockKey = "lock:sms:send:" + phonenumber;
        RLock lock = redissonClient.getLock(lockKey);
        
        boolean isLocked = false;
        try {
            // 尝试获取锁，等待2秒，持锁10秒
            isLocked = lock.tryLock(2, 10, TimeUnit.SECONDS);
            if (!isLocked) {
                log.warn("获取短信发送分布式锁失败，手机号: {}", phonenumber);
                throw new ServiceException("操作过于频繁，请稍后再试");
            }

            // 再次校验是否已存在未过期的验证码
            String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + phonenumber;
            String existingCode = redisService.getCacheObject(verifyKey);
            if (StringUtils.isNotEmpty(existingCode)) {
                throw new ServiceException("验证码仍在有效期内，请勿重复发送");
            }

            // 3. 生成 6 位随机验证码
            String code = generateCode();

            // 4. 调用阿里云短信服务发送验证码（模拟）
            sendToAliyun(phonenumber, code);

            // 5. 保存验证码到 Redis，有效期 5 分钟
            redisService.setCacheObject(verifyKey, code, 5L, TimeUnit.MINUTES);
            
            log.info("手机号 {} 验证码发送成功：{}", phonenumber, code);

        } catch (InterruptedException e) {
            log.error("获取短信发送锁被中断", e);
            Thread.currentThread().interrupt();
            throw new ServiceException("系统繁忙，请稍后再试");
        } finally {
            // 释放锁
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 三层分布式限流防刷
     */
    private void checkRateLimit(String phonenumber, String ip) {
        // 1. 手机号：60秒内最多发送1次
        String phoneSecKey = "rate_limit:sms:phone:sec:" + phonenumber;
        if (!rateLimiterService.isAllowed(phoneSecKey, 60000, 1)) {
            throw new ServiceException("该手机号获取验证码过于频繁，请1分钟后再试");
        }

        // 2. 手机号：24小时内最多发送10次
        String phoneDayKey = "rate_limit:sms:phone:day:" + phonenumber;
        if (!rateLimiterService.isAllowed(phoneDayKey, 86400000, 10)) {
            throw new ServiceException("该手机号今日获取验证码次数已达上限");
        }

        // 3. IP地址：1小时内最多发送30次，防止恶意IP肉鸡刷量
        String ipHourKey = "rate_limit:sms:ip:hour:" + ip;
        if (!rateLimiterService.isAllowed(ipHourKey, 3600000, 30)) {
            throw new ServiceException("当前网络环境异常，请稍后再试");
        }
    }

    /**
     * 模拟对接阿里云短信 API
     */
    private void sendToAliyun(String phonenumber, String code) {
        // 实际企业项目中这里会通过 HttpClient 或阿里云 SDK 调用外部接口
        log.info("======> 阿里云 SMS: 正在向 {} 发送短信，验证码为 {}", phonenumber, code);
        // ... (SDK调用逻辑)
    }

    /**
     * 随机生成 6 位数字验证码
     */
    private String generateCode() {
        return String.valueOf((int) ((Math.random() * 9 + 1) * 100000));
    }
}
