package com.ruoyi.system.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.constant.ServiceNameConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.system.api.factory.RemoteTokenFallbackFactory;

/**
 * 代币/计费远程调用服务
 * 用于跨微服务的企业级账务一致性操作
 */
@FeignClient(contextId = "remoteTokenService", value = ServiceNameConstants.SYSTEM_SERVICE, fallbackFactory = RemoteTokenFallbackFactory.class)
public interface RemoteTokenService {

    /**
     * 扣减用户代币/余额 (分布式事务 Seata 支持)
     *
     * @param userId 用户ID
     * @param tokens 要扣减的代币数
     * @param source 请求来源
     * @return 结果
     */
    @PutMapping("/user/deductToken/{userId}/{tokens}")
    public R<Boolean> deductToken(@PathVariable("userId") Long userId, @PathVariable("tokens") Long tokens, @RequestHeader(SecurityConstants.FROM_SOURCE) String source);
}
