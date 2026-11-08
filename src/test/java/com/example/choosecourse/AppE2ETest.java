package com.example.choosecourse;

import com.example.choosecourse.auth.CaptchaService;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.LocalTime;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 完整链路：真实嵌入式 PostgreSQL + Flyway + Spring Security 登录 + 学生课程页。
 * 不需要 Docker，也不需要外部数据库。
 */
@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.sql.init.mode=never"
})
@AutoConfigureMockMvc
@Import(AppE2ETest.EmbeddedPostgresConfig.class)
class AppE2ETest {

    @TestConfiguration(proxyBeanMethods = false)
    static class EmbeddedPostgresConfig {
        @Bean(destroyMethod = "close")
        EmbeddedPostgres embeddedPostgres() throws IOException {
            return EmbeddedPostgres.start();
        }

        @Bean
        DataSource dataSource(EmbeddedPostgres postgres) {
            return postgres.getPostgresDatabase();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void seed() {
        jdbc.update("delete from enrollments");
        jdbc.update("delete from completed_courses");
        jdbc.update("delete from course_prerequisites");
        jdbc.update("delete from course_sessions");
        jdbc.update("delete from courses");
        jdbc.update("delete from students");

        jdbc.update("""
                insert into students (sno, name, grade, major, college, password_hash, phone)
                values (?, ?, ?, ?, ?, ?, ?)
                """, "20200001", "测试学生", "2020级", "计算机",
                "信息学院", passwordEncoder.encode("123456"), "13800000000");
        jdbc.update("""
                insert into courses (cno, name, teacher, location, capacity, is_active)
                values (?, ?, ?, ?, ?, true)
                """, "t001", "数据结构", "张老师", "A101", 10);
        jdbc.update("""
                insert into course_sessions (cno, day_of_week, start_time, end_time)
                values (?, ?, ?, ?)
                """, "t001", 1, LocalTime.of(8, 30), LocalTime.of(10, 10));
    }

    @Test
    void loginThenViewCoursePage() throws Exception {
        MockHttpSession captchaSession = new MockHttpSession();
        captchaSession.setAttribute(CaptchaService.SESSION_KEY, "ABCD");

        MvcResult loginResult = mockMvc.perform(post("/login")
                        .session(captchaSession)
                        .with(csrf())
                        .param("Id", "20200001")
                        .param("Pwd", "123456")
                        .param("captcha", "ABCD"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/student/courses"))
                .andReturn();

        MockHttpSession authenticatedSession =
                (MockHttpSession) loginResult.getRequest().getSession(false);

        mockMvc.perform(get("/student/courses").session(authenticatedSession))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("数据结构")))
                .andExpect(content().string(containsString("可选")));
    }

    @Test
    void adminLoginRedirectsToAdminCourses() throws Exception {
        jdbc.update("""
                insert into admins (admin_id, name, password_hash, phone)
                values (?, ?, ?, ?)
                """, "admin", "管理员", passwordEncoder.encode("admin123"), "13900000000");

        MockHttpSession captchaSession = new MockHttpSession();
        captchaSession.setAttribute(CaptchaService.SESSION_KEY, "WXYZ");

        mockMvc.perform(post("/login")
                        .session(captchaSession)
                        .with(csrf())
                        .param("Id", "admin")
                        .param("Pwd", "admin123")
                        .param("captcha", "WXYZ"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/courses"));
    }
}
