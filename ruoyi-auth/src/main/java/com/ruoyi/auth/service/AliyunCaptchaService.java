package com.ruoyi.auth.service;

import com.aliyun.captcha20230305.Client;
import com.aliyun.captcha20230305.models.VerifyIntelligentCaptchaRequest;
import com.aliyun.captcha20230305.models.VerifyIntelligentCaptchaResponse;
import com.aliyun.teaopenapi.models.Config;
import com.ruoyi.common.core.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;

/**
 * 阿里云验证码 2.0 (风控人机验证) 核心服务
 * 防止黑客批量刷短信的最强防线
 */
@Service
public class AliyunCaptchaService {

    private static final Logger log = LoggerFactory.getLogger(AliyunCaptchaService.class);

    // TODO: 真实项目中可以从 Nacos / YML 中获取
    @Value("${aliyun.captcha.accessKeyId:your_access_key_id}")
    private String accessKeyId;

    @Value("${aliyun.captcha.accessKeySecret:your_access_key_secret}")
    private String accessKeySecret;

    @Value("${aliyun.captcha.sceneId:your_scene_id}")
    private String sceneId;

    private Client captchaClient;

    @PostConstruct
    public void initClient() {
        try {
            Config config = new Config()
                    .setAccessKeyId(accessKeyId)
                    .setAccessKeySecret(accessKeySecret)
                    .setEndpoint("captcha.cn-shanghai.aliyuncs.com");
            this.captchaClient = new Client(config);
            log.info("阿里云验证码 2.0 客户端初始化成功");
        } catch (Exception e) {
            log.error("阿里云验证码 2.0 客户端初始化失败", e);
        }
    }

    /**
     * 校验风控 Ticket 是否有效
     *
     * @param captchaVerifyParam 前端滑动拼图后由阿里云风控下发的 Ticket 凭证
     * @return boolean 验证结果
     */
    public boolean verify(String captchaVerifyParam) {
        if (captchaVerifyParam == null || captchaVerifyParam.trim().isEmpty()) {
            throw new ServiceException("缺失人机验证凭证，拒绝请求");
        }

        try {
            VerifyIntelligentCaptchaRequest request = new VerifyIntelligentCaptchaRequest()
                    .setCaptchaVerifyParam(captchaVerifyParam)
                    .setSceneId(sceneId);

            log.info("【风控人机验证】开始调用阿里云二次校验接口，验证参数长度: {}", captchaVerifyParam.length());
            
            // 发起二次校验
            VerifyIntelligentCaptchaResponse response = captchaClient.verifyIntelligentCaptcha(request);

            Boolean isSuccess = response.getBody().getResult().getVerifyResult();
            if (Boolean.TRUE.equals(isSuccess)) {
                log.info("【风控人机验证】验证通过");
                return true;
            } else {
                log.warn("【风控人机验证】验证失败，疑似黑客恶意流量，风控拦截返回码: {}", response.getBody().getResult().getVerifyCode());
                throw new ServiceException("人机风控校验未通过，请重新验证");
            }
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("【风控人机验证】服务端调用阿里云 API 异常", e);
            // 这里出于可用性考虑：如果阿里云自己挂了，可以选择放行(true)或报错。企业级一般建议严格拦截或降级。
            throw new ServiceException("人机验证服务暂时不可用，请稍后再试");
        }
    }
}
