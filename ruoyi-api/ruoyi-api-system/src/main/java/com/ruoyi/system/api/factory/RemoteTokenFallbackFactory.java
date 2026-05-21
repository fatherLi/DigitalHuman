package com.ruoyi.system.api.factory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import com.ruoyi.common.core.domain.R;
import com.ruoyi.system.api.RemoteTokenService;

/**
 * 代币服务降级处理
 */
@Component
public class RemoteTokenFallbackFactory implements FallbackFactory<RemoteTokenService> {

    private static final Logger log = LoggerFactory.getLogger(RemoteTokenFallbackFactory.class);

    @Override
    public RemoteTokenService create(Throwable throwable) {
        log.error("代币服务调用失败:{}", throwable.getMessage());
        return new RemoteTokenService() {
            @Override
            public R<Boolean> deductToken(Long userId, Long tokens, String source) {
                return R.fail("扣减用户代币失败:" + throwable.getMessage());
            }
        };
    }
}
