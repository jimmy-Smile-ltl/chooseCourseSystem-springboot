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
 * 默认禁止演示种子/演示登录预填，避免把演示账号和密码暴露到生产页面。
 * 公开作品演示必须额外设置 APP_DEMO_PUBLIC=true 显式确认。
 */
@Component
@Profile("prod")
public class DemoModeGuard implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoModeGuard.class);

    private final boolean demoSeed;
    private final boolean demoLogin;
    private final boolean demoPublic;

    public DemoModeGuard(@Value("${app.demo-seed:false}") boolean demoSeed,
                         @Value("${app.demo-login:false}") boolean demoLogin,
                         @Value("${app.demo-public:false}") boolean demoPublic) {
        this.demoSeed = demoSeed;
        this.demoLogin = demoLogin;
        this.demoPublic = demoPublic;
    }

    @Override
    public void run(ApplicationArguments args) {
        if ((demoSeed || demoLogin) && !demoPublic) {
            throw new IllegalStateException(
                    "生产环境禁止开启 APP_DEMO_SEED / APP_DEMO_LOGIN；"
                            + "如果是公开作品演示，请额外设置 APP_DEMO_PUBLIC=true。");
        }
        if (demoPublic && (demoSeed || demoLogin)) {
            log.warn("公开演示模式已开启：登录页会自动填充演示账号，访客可修改演示数据。");
        } else {
            log.info("生产安全检查通过：演示模式已关闭");
        }
    }
}
