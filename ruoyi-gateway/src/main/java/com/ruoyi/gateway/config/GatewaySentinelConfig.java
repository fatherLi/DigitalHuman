package com.ruoyi.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.common.SentinelGatewayConstants;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiDefinition;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPathPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.ApiPredicateItem;
import com.alibaba.csp.sentinel.adapter.gateway.common.api.GatewayApiDefinitionManager;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayFlowRule;
import com.alibaba.csp.sentinel.adapter.gateway.common.rule.GatewayRuleManager;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import java.util.HashSet;
import java.util.Set;

/**
 * 网关限流配置
 * 针对短信接口在网关层面直接进行防刷降级
 */
@Configuration
public class GatewaySentinelConfig {

    @PostConstruct
    public void doInit() {
        // 加载自定义的 API 分组
        initCustomizedApis();
        // 加载网关限流规则
        initGatewayRules();
    }

    private void initCustomizedApis() {
        Set<ApiDefinition> definitions = new HashSet<>();
        
        // 专门为短信接口定义一个 API 组
        ApiDefinition api1 = new ApiDefinition("sms_api_group")
                .setPredicateItems(new HashSet<ApiPredicateItem>() {{
                    add(new ApiPathPredicateItem().setPattern("/auth/sms/sendLoginCode").setMatchStrategy(SentinelGatewayConstants.URL_MATCH_STRATEGY_EXACT));
                }});
        
        definitions.add(api1);
        GatewayApiDefinitionManager.loadApiDefinitions(definitions);
    }

    private void initGatewayRules() {
        Set<GatewayFlowRule> rules = new HashSet<>();

        // 针对短信接口组，限制 QPS 为 10，超过的请求直接在网关层被拒绝 (快速失败)
        // 这样可以有效防止黑客利用突发高流量直接打挂后端的 Auth 认证中心
        rules.add(new GatewayFlowRule("sms_api_group")
                .setCount(10) // QPS 阈值
                .setIntervalSec(1) // 统计时间窗口 1秒
                .setBurst(5) // 允许应对突发的 5 个额外请求
                .setParamItem(null)
        );

        GatewayRuleManager.loadRules(rules);
    }
}
