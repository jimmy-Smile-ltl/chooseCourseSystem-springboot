package com.example.choosecourse.web;

import com.example.choosecourse.auth.CaptchaService;
import com.example.choosecourse.auth.LoginAttemptService;
import com.example.choosecourse.service.PasswordResetService;
import com.example.choosecourse.util.ClientIps;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class PasswordResetController {

    private final PasswordResetService passwordResetService;
    private final LoginAttemptService loginAttemptService;

    public PasswordResetController(PasswordResetService passwordResetService,
                                   LoginAttemptService loginAttemptService) {
        this.passwordResetService = passwordResetService;
        this.loginAttemptService = loginAttemptService;
    }

    @GetMapping("/forgot-password")
    public String form() {
        return "forgot-password";
    }

    @PostMapping("/forgot-password")
    public String reset(@RequestParam(name = "id") String id,
                        @RequestParam(name = "phone") String phone,
                        @RequestParam(name = "newPassword") String newPassword,
                        @RequestParam(name = "confirmPassword") String confirmPassword,
                        @RequestParam(name = "who", defaultValue = "student") String who,
                        @RequestParam(name = "captcha", required = false) String captcha,
                        HttpSession session,
                        HttpServletRequest request,
                        Model model) {
        String rateKey = "forgot:" + ClientIps.resolve(request);
        if (loginAttemptService.isBlocked(rateKey)) {
            model.addAttribute("error", "操作过于频繁，请 5 分钟后再试。");
            return keepForm(model, who, id, phone);
        }
        loginAttemptService.record(rateKey);

        String expected = (String) session.getAttribute(CaptchaService.SESSION_KEY);
        session.removeAttribute(CaptchaService.SESSION_KEY);
        if (expected == null || captcha == null || !expected.equalsIgnoreCase(captcha.trim())) {
            model.addAttribute("error", "验证码错误，请重试。");
            return keepForm(model, who, id, phone);
        }

        String trimmedId = id.trim();
        String trimmedPhone = phone.trim();

        if (!StringUtils.hasText(trimmedId) || !StringUtils.hasText(trimmedPhone)) {
            model.addAttribute("error", "账号和密保手机不能为空。");
            return keepForm(model, who, id, phone);
        }
        if (newPassword == null || newPassword.length() < 4) {
            model.addAttribute("error", "新密码至少 4 位。");
            return keepForm(model, who, id, phone);
        }
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("error", "两次输入的新密码不一致。");
            return keepForm(model, who, id, phone);
        }

        boolean reset = passwordResetService.reset(who, trimmedId, trimmedPhone, newPassword);
        if (!reset) {
            model.addAttribute("error", "账号与密保手机不匹配。");
            return keepForm(model, who, id, phone);
        }
        return "redirect:/login?reset";
    }

    private String keepForm(Model model, String who, String id, String phone) {
        model.addAttribute("who", who);
        model.addAttribute("id", id);
        model.addAttribute("phone", phone);
        return "forgot-password";
    }
}
