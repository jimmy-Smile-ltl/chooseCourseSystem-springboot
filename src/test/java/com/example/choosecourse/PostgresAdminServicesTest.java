package com.example.choosecourse;

import com.example.choosecourse.service.AdminCourseService;
import com.example.choosecourse.service.AdminStudentService;
import com.example.choosecourse.service.CourseQueryService;
import com.example.choosecourse.service.PasswordResetService;
import com.example.choosecourse.service.StudentAccountService;
import com.example.choosecourse.web.form.CourseForm;
import com.example.choosecourse.web.form.StudentForm;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理端 CRUD / 学生账号服务在真实 PostgreSQL 上的集成测试。
 */
class PostgresAdminServicesTest {

    @Test
    void adminCrudAndAccountServicesWorkOnPostgres() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            seed(jdbc);

            PasswordEncoder encoder = new BCryptPasswordEncoder();
            AdminCourseService adminCourseService = new AdminCourseService(jdbc);
            CourseQueryService courseQueryService = new CourseQueryService(jdbc);

            CourseForm course = new CourseForm();
            course.setCno("t010");
            course.setName("数据库原理");
            course.setTeacher("周老师");
            course.setLocation("F606");
            course.setCapacity(5);
            course.setActive(true);
            course.setDayOfWeek(3);
            course.setStartTime(LocalTime.of(13, 0));
            course.setEndTime(LocalTime.of(15, 35));
            course.setPrerequisiteCnos(List.of("t001"));

            assertTrue(adminCourseService.create(course).isEmpty());
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from course_sessions where cno = 't010'", Integer.class));
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from course_prerequisites where cno = 't010'", Integer.class));

            course.setCapacity(8);
            assertTrue(adminCourseService.update("t010", course).isEmpty());
            assertEquals(8, courseQueryService.findForm("t010").orElseThrow().getCapacity());

            assertTrue(adminCourseService.toggleActive("t010"));
            assertFalse(jdbc.queryForObject(
                    "select is_active from courses where cno = 't010'", Boolean.class));
            assertTrue(adminCourseService.toggleActive("t010"));
            assertTrue(adminCourseService.delete("t010").isEmpty());

            AdminStudentService adminStudentService = new AdminStudentService(jdbc, encoder);
            StudentForm student = new StudentForm();
            student.setSno("20200002");
            student.setName("新学生");
            student.setGrade("2024级");
            student.setMajor("软件工程");
            student.setCollege("信息学院");
            student.setPhone("13900000000");
            student.setPassword("abcdef");
            student.setCompletedCnos(List.of("t001"));

            assertTrue(adminStudentService.create(student).isEmpty());
            String hash = jdbc.queryForObject(
                    "select password_hash from students where sno = '20200002'", String.class);
            assertTrue(encoder.matches("abcdef", hash));
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from completed_courses where sno = '20200002'", Integer.class));

            student.setName("新名字");
            student.setPassword("");
            student.setCompletedCnos(List.of());
            assertTrue(adminStudentService.update("20200002", student).isEmpty());
            assertEquals("新名字", jdbc.queryForObject(
                    "select name from students where sno = '20200002'", String.class));
            assertTrue(encoder.matches("abcdef", jdbc.queryForObject(
                    "select password_hash from students where sno = '20200002'", String.class)));
            assertEquals(0, jdbc.queryForObject(
                    "select count(*) from completed_courses where sno = '20200002'", Integer.class));

            StudentAccountService accountService = new StudentAccountService(jdbc, encoder);
            assertTrue(accountService.changePassword("20200002", "abcdef", "654321", "654321").isEmpty());
            assertTrue(accountService.changePhone("20200002", "654321", "13700000000").isEmpty());
            assertEquals("13700000000", jdbc.queryForObject(
                    "select phone from students where sno = '20200002'", String.class));

            PasswordResetService resetService = new PasswordResetService(jdbc, encoder);
            assertTrue(resetService.reset("student", "20200002", "13700000000", "newpass"));
            assertTrue(encoder.matches("newpass", jdbc.queryForObject(
                    "select password_hash from students where sno = '20200002'", String.class)));

            assertTrue(adminStudentService.delete("20200002").isEmpty());
            assertEquals(0, jdbc.queryForObject(
                    "select count(*) from students where sno = '20200002'", Integer.class));
        }
    }

    private void seed(JdbcTemplate jdbc) {
        jdbc.update("""
                insert into students (sno, name, grade, major, college, password_hash, phone)
                values ('20200001', '测试学生', '2020级', '计算机', '信息学院', 'x', '13800000000')
                """);
        jdbc.update("""
                insert into courses (cno, name, teacher, location, capacity, is_active)
                values ('t001', '数据结构', '张老师', 'A101', 10, true)
                """);
        jdbc.update("""
                insert into course_sessions (cno, day_of_week, start_time, end_time)
                values ('t001', 1, ?, ?)
                """, LocalTime.of(8, 30), LocalTime.of(10, 10));
    }
}
