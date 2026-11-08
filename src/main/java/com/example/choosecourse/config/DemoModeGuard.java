package com.example.choosecourse.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 生产 profile 下的最后一道保险：
 * 一旦开启演示种子或演示登录预填，直接拒绝启动，避免把演示账号和密码暴露到生产页面。
 */
@Component
@Profile("prod")
public class DemoModeGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoModeGuard.class);

    private final boolean demoSeed;
    private final boolean demoLogin;

    public DemoModeGuard(@Value("${app.demo-seed:false}") boolean demoSeed,
                         @Value("${app.demo-login:false}") boolean demoLogin) {
        this.demoSeed = demoSeed;
        this.demoLogin = demoLogin;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (demoSeed || demoLogin) {
            throw new IllegalStateException(
                    "生产环境禁止开启 APP_DEMO_SEED / APP_DEMO_LOGIN；请关闭演示开关后重启。");
        }
        log.info("生产安全检查通过：演示模式已关闭");
    }
}
