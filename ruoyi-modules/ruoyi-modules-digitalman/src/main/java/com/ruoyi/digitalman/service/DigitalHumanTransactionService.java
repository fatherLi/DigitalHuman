package com.ruoyi.digitalman.service;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.digitalman.mq.DigitalHumanTaskProducer;
import com.ruoyi.system.api.RemoteTokenService;
import io.seata.spring.annotation.GlobalTransactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 亮点四：分布式事务 (Seata) 闭环控制
 * 保障“用户代币扣减”与“大模型/数字人调度”的最终数据一致性
 */
@Service
public class DigitalHumanTransactionService {

    private static final Logger log = LoggerFactory.getLogger(DigitalHumanTransactionService.class);

    @Autowired
    private RemoteTokenService remoteTokenService;

    @Autowired
    private DigitalHumanTaskProducer taskProducer;

    /**
     * 优化后的方案：仅对“扣费”这一原子操作进行分布式事务控制
     * 数字人任务的发送，通过 MQ 来保证最终一致性，不占用事务锁
     */
    @GlobalTransactional(name = "digitalman-token-deduct", rollbackFor = Exception.class)
    public boolean deductAndPrepareTask(Long userId, Long tokens, String text) {
        log.info("【Seata分布式事务】开始扣费，用户: {}", userId);

        // 1. 原子扣费（只在这个过程中锁数据库）
        R<Boolean> result = remoteTokenService.deductToken(userId, tokens, SecurityConstants.INNER);

        if (result == null || !result.isSuccess()) {
            throw new ServiceException("扣费服务异常，事务自动回滚");
        }

        // 2. 扣费成功后，通过 MQ 发送任务
        // 注意：这里不需要在事务内完成，因为如果这一步发送失败，
        // 你可以在 MQ 生产者端通过 confirm 机制重试，这比锁住整个数据库高效得多
        try {
            taskProducer.sendTask(userId, text);
        } catch (Exception e) {
            log.error("扣费成功但 MQ 发送任务失败，准备手动补偿...");
            // 如果 MQ 发送失败，抛出异常回滚扣费
            throw new ServiceException("渲染任务下发失败，代币已回滚");
        }

        return true;
    }
}
