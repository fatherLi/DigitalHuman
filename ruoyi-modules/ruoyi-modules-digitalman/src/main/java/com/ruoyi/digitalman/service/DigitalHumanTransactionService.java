package com.ruoyi.digitalman.service;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.exception.ServiceException;
import com.ruoyi.digitalman.mq.DigitalHumanTaskProducer;
import com.ruoyi.system.api.RemoteUserService;
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
    private RemoteUserService remoteUserService;

    @Autowired
    private DigitalHumanTaskProducer taskProducer;

    /**
     * 扣除用户代币 (开启 Seata AT 全局事务)
     * 如果大模型 API 限流或系统异常，自动回滚已扣除的代币
     */
    @GlobalTransactional(name = "digitalman-chat-deduct", rollbackFor = Exception.class)
    public void deductUserToken(Long userId, Long tokens) {
        log.info("【Seata分布式事务】开始执行代币扣减，用户: {}, 扣除数量: {}", userId, tokens);
        
        // 1. 调用远程系统服务扣除代币
        R<Boolean> result = remoteUserService.deductToken(userId, tokens, SecurityConstants.INNER);
        
        if (R.FAIL == result.getCode() || !result.getData()) {
            log.error("【Seata分布式事务】代币扣减失败，抛出异常触发全局回滚。原因: {}", result.getMsg());
            throw new ServiceException("代币余额不足或扣减失败");
        }
        
        log.info("【Seata分布式事务】代币扣减成功！");
    }

    /**
     * 发送数字人视频渲染任务
     * （这个操作通常在长连接/大模型对话完整结束后被调用，属于最终一致性流程）
     */
    public void sendDigitalHumanTask(Long userId, String text) {
        // 利用 RabbitMQ 可靠消息发送渲染任务
        taskProducer.sendTask(userId, text);
    }
}
