package com.lifetrack.config;

import com.lifetrack.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** 演示身份认证必须同时满足显式 dev 环境和对应开关；混合环境一律关闭。 */
@Component
public class DevelopmentAuthPolicy {
    private final Environment environment;
    private final boolean devLogin;
    private final boolean demoPhone;
    private final String demoCode;

    public DevelopmentAuthPolicy(Environment environment,
            @Value("${auth.dev-login-enabled:false}") boolean devLogin,
            @Value("${auth.demo-phone-enabled:false}") boolean demoPhone,
            @Value("${auth.demo-code:123456}") String demoCode) {
        this.environment = environment; this.devLogin = devLogin; this.demoPhone = demoPhone; this.demoCode = demoCode;
        if (demoCode == null || !demoCode.matches("[0-9]{6}")) throw new IllegalStateException("演示验证码配置必须为6位数字");
    }
    private boolean developmentOnly() {
        String[] profiles = environment.getActiveProfiles();
        return profiles.length == 1 && "dev".equals(profiles[0]);
    }
    public boolean devLoginEnabled() { return developmentOnly() && devLogin; }
    public boolean demoPhoneEnabled() { return developmentOnly() && demoPhone; }
    public String demoCode() { return demoPhoneEnabled() ? demoCode : null; }
    public void requireDevLogin() {
        if (!devLoginEnabled()) throw new BusinessException(403, "当前环境未启用开发登录");
    }
    public void requireDemoPhone() {
        if (!demoPhoneEnabled()) throw new BusinessException(403, "当前环境未启用演示手机号登录");
    }
}
