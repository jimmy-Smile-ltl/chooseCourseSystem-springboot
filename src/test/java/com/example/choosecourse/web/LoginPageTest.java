package com.example.choosecourse.web;

import com.example.choosecourse.auth.CaptchaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 不依赖 PostgreSQL 的 Web 冒烟测试：登录页、验证码、未登录拦截。
 * 选课/数据库事务需要真实 PostgreSQL，后续用 Testcontainers 或 Neon 测试分支补。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:choose_course;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false"
})
@AutoConfigureMockMvc
class LoginPageTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginPageRenders() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("选课系统")))
                .andExpect(content().string(containsString("验证码")));
    }

    @Test
    void studentAreaRedirectsAnonymousUserToLogin() throws Exception {
        mockMvc.perform(get("/student/courses"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    void captchaEndpointReturnsSvg() throws Exception {
        mockMvc.perform(get("/captcha"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<svg")))
                .andExpect(content().string(containsString("<text")));
    }

    @Test
    void loginWithoutCaptchaRedirectsToCaptchaError() throws Exception {
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("Id", "20200001")
                        .param("Pwd", "123456"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?captchaError"));
    }

    @Test
    void loginWithWrongCredentialsAndValidCaptchaRedirectsToError() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CaptchaService.SESSION_KEY, "ABCD");

        mockMvc.perform(post("/login")
                        .session(session)
                        .with(csrf())
                        .param("Id", "nobody")
                        .param("Pwd", "bad")
                        .param("captcha", "abcd"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    void forgotPasswordPageRendersCaptcha() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("验证码")));
    }

    @Test
    void forgotPasswordWithWrongCaptchaShowsError() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(CaptchaService.SESSION_KEY, "ABCD");

        mockMvc.perform(post("/forgot-password")
                        .session(session)
                        .with(csrf())
                        .param("who", "student")
                        .param("id", "20200001")
                        .param("phone", "13800000000")
                        .param("newPassword", "1234")
                        .param("confirmPassword", "1234")
                        .param("captcha", "WRONG"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("验证码错误")));
    }
}
