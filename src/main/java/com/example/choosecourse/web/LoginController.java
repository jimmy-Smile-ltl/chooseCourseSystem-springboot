package com.example.choosecourse.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class LoginController {

    private final boolean demoLogin;

    public LoginController(@Value("${app.demo-login:false}") boolean demoLogin) {
        this.demoLogin = demoLogin;
    }

    @GetMapping("/login")
    public String login(@RequestParam(name = "error", required = false) String error,
                        @RequestParam(name = "logout", required = false) String logout,
                        @RequestParam(name = "reset", required = false) String reset,
                        @RequestParam(name = "captchaError", required = false) String captchaError,
                        @RequestParam(name = "rateLimited", required = false) String rateLimited,
                        Model model) {
        if (error != null) {
            model.addAttribute("error", "账号或密码错误，请重试。");
        }
        if (captchaError != null) {
            model.addAttribute("error", "验证码错误，请重试。");
        }
        if (rateLimited != null) {
            model.addAttribute("error", "登录尝试过于频繁，请 5 分钟后再试。");
        }
        if (logout != null) {
            model.addAttribute("message", "已退出登录。");
        }
        if (reset != null) {
            model.addAttribute("message", "密码已重置，请使用新密码登录。");
        }
        model.addAttribute("demoLogin", demoLogin);
        if (demoLogin) {
            model.addAttribute("demoId", "20200001");
            model.addAttribute("demoPwd", "123456");
        }
        return "login";
    }

    @GetMapping("/")
    public String home(Authentication authentication) {
        if (authentication == null) {
            return "redirect:/login";
        }
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        return "redirect:" + (admin ? "/admin/courses" : "/student/courses");
    }
}
