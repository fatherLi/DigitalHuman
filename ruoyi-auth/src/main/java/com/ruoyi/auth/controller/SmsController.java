package com.ruoyi.auth.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.auth.service.SmsService;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.utils.ip.IpUtils;
import jakarta.servlet.http.HttpServletRequest;

/**
 * 短信接口控制
 */
@RestController
@RequestMapping("/sms")
public class SmsController {

    @Autowired
    private SmsService smsService;

    /**
     * 发送登录短信验证码
     */
    @PostMapping("/sendLoginCode")
    public R<?> sendLoginCode(@RequestParam("phonenumber") String phonenumber, HttpServletRequest request) {
        String ip = IpUtils.getIpAddr();
        smsService.sendLoginSms(phonenumber, ip);
        return R.ok("短信发送成功");
    }
}
