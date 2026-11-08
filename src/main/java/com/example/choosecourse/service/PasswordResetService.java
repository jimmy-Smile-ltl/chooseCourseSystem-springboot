package com.example.choosecourse.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 旧系统的“忘记密码”逻辑：账号 + 密保手机匹配即可重置。
 * 这里保留该功能，但密码改为 BCrypt 存储。
 *
 * 注意：这不是强安全方案。上线演示时至少应加验证码/限流；
 * 更稳妥的做法是管理员重置或短信验证码。
 */
@Service
public class PasswordResetService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public boolean reset(String who, String id, String phone, String newPassword) {
        String hash = passwordEncoder.encode(newPassword);
        int updated;
        if ("admin".equalsIgnoreCase(who)) {
            updated = jdbc.update("""
                    update admins
                    set password_hash = ?
                    where admin_id = ? and phone = ?
                    """, hash, id, phone);
        } else {
            updated = jdbc.update("""
                    update students
                    set password_hash = ?
                    where sno = ? and phone = ?
                    """, hash, id, phone);
        }
        return updated == 1;
    }
}
