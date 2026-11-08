package com.example.choosecourse.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.example.choosecourse.util.ClientIps;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 登录 POST 前的守卫：IP 限流 + 服务端验证码校验。
 * 放在 UsernamePasswordAuthenticationFilter 之前。
 *
 * 注意：这个类故意不加 @Component，由 SecurityConfig 手动 new。
 * 否则 Spring Boot 会把它额外注册成 Servlet Filter，导致执行两次。
 */
public class LoginGuardFilter extends OncePerRequestFilter {

    private final CaptchaService captchaService;
    private final LoginAttemptService loginAttemptService;

    public LoginGuardFilter(CaptchaService captchaService,
                            LoginAttemptService loginAttemptService) {
        this.captchaService = captchaService;
        this.loginAttemptService = loginAttemptService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        // 不依赖 servletPath：在 MockMvc / 不同容器下它的值可能为空。
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"/login".equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String ip = ClientIps.resolve(request);
        if (loginAttemptService.isBlocked(ip)) {
            response.sendRedirect(request.getContextPath() + "/login?rateLimited");
            return;
        }
        loginAttemptService.record(ip);

        HttpSession session = request.getSession(false);
        String expected = session == null ? null : (String) session.getAttribute(CaptchaService.SESSION_KEY);
        String actual = request.getParameter("captcha");

        if (expected == null || actual == null || !expected.equalsIgnoreCase(actual.trim())) {
            if (session != null) {
                session.removeAttribute(CaptchaService.SESSION_KEY);
            }
            response.sendRedirect(request.getContextPath() + "/login?captchaError");
            return;
        }

        session.removeAttribute(CaptchaService.SESSION_KEY);
        filterChain.doFilter(request, response);
    }
}
