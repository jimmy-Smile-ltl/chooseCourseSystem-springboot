package com.example.choosecourse.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class StudentAccountService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public StudentAccountService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public List<String> changePassword(String sno, String oldPassword,
                                       String newPassword, String confirmPassword) {
        List<String> errors = new ArrayList<>();
        if (oldPassword == null || oldPassword.isBlank()) {
            errors.add("请输入原密码");
        }
        if (newPassword == null || newPassword.length() < 4) {
            errors.add("新密码至少 4 位");
        }
        if (newPassword != null && !newPassword.equals(confirmPassword)) {
            errors.add("两次输入的新密码不一致");
        }

        String storedHash = findPasswordHash(sno);
        if (storedHash == null) {
            errors.add("学生不存在");
        } else if (oldPassword != null && !passwordEncoder.matches(oldPassword, storedHash)) {
            errors.add("原密码错误");
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        jdbc.update("update students set password_hash = ? where sno = ?",
                passwordEncoder.encode(newPassword), sno);
        return errors;
    }

    @Transactional
    public List<String> changePhone(String sno, String password, String newPhone) {
        List<String> errors = new ArrayList<>();
        if (newPhone == null || newPhone.isBlank()) {
            errors.add("请输入新的密保手机");
        } else if (newPhone.trim().length() > 20) {
            errors.add("手机号码过长");
        }
        if (password == null || password.isBlank()) {
            errors.add("请输入当前密码确认身份");
        }

        String storedHash = findPasswordHash(sno);
        if (storedHash == null) {
            errors.add("学生不存在");
        } else if (password != null && !passwordEncoder.matches(password, storedHash)) {
            errors.add("密码错误");
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        jdbc.update("update students set phone = ? where sno = ?", newPhone.trim(), sno);
        return errors;
    }

    private String findPasswordHash(String sno) {
        List<String> hashes = jdbc.queryForList(
                "select password_hash from students where sno = ?", String.class, sno);
        return hashes.isEmpty() ? null : hashes.getFirst();
    }
}
