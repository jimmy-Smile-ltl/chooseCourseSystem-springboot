package com.example.choosecourse.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 学生和管理员共用 accounts 视图登录。
 * accounts = students ∪ admins，role 为 STUDENT / ADMIN。
 */
@Service
public class DbUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbc;

    public DbUserDetailsService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        List<AccountRow> accounts = jdbc.query("""
                select username, password_hash, user_role
                from accounts
                where username = ?
                """, (rs, rowNum) -> new AccountRow(
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("user_role")
        ), username);

        if (accounts.isEmpty()) {
            throw new UsernameNotFoundException("账号不存在: " + username);
        }
        if (accounts.size() > 1) {
            // 迁移前应在 ETL 里校验学号和管理员 ID 不冲突
            throw new UsernameNotFoundException("账号同时存在于学生和管理员表，请检查数据: " + username);
        }

        AccountRow account = accounts.getFirst();
        return User.withUsername(account.username())
                .password(account.passwordHash())
                .roles(account.role())
                .build();
    }

    private record AccountRow(String username, String passwordHash, String role) {
    }
}
