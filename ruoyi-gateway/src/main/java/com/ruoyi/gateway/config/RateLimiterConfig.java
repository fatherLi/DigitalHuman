package com.ruoyi.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import reactor.core.publisher.Mono;

/**
 * 路由限流配置 (企业级网关Redis限流)
 * 
 * @author ruoyi
 */
@Configuration
public class RateLimiterConfig {

    /**
     * IP限流规则 (默认)
     */
    @Primary
    @Bean
    public KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
    }

    /**
     * 用户限流规则 (根据请求头中的用户标识)
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> Mono.just(exchange.getRequest().getHeaders().getFirst("user_id") != null 
                ? exchange.getRequest().getHeaders().getFirst("user_id") 
                : exchange.getRequest().getRemoteAddress().getAddress().getHostAddress());
    }

    /**
     * 接口路径限流规则
     */
    @Bean
    public KeyResolver pathKeyResolver() {
        return exchange -> Mono.just(exchange.getRequest().getURI().getPath());
    }
}
