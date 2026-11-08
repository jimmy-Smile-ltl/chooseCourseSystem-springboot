package com.example.choosecourse;

import com.example.choosecourse.service.CourseQueryService;
import com.example.choosecourse.service.EnrollmentService;
import com.example.choosecourse.web.dto.EnrollmentOutcome;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 真实 PostgreSQL 集成测试（嵌入式 Postgres，无需 Docker）。
 * 验证 Flyway schema、accounts 视图、课程聚合查询和选课事务 SQL。
 */
class PostgresIntegrationTest {

    @Test
    void flywaySchemaAndEnrollmentFlowWorkOnPostgres() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            seed(jdbc);

            // accounts 视图
            List<String> roles = jdbc.queryForList(
                    "select user_role from accounts where username = ?", String.class, "20200001");
            assertEquals(List.of("STUDENT"), roles);

            CourseQueryService courseQueryService = new CourseQueryService(jdbc);
            var courses = courseQueryService.listForStudent("20200001");
            assertEquals(5, courses.size());
            assertEquals("周一 08:30-10:10", courses.stream()
                    .filter(course -> course.getCno().equals("t001"))
                    .findFirst().orElseThrow().getTimeText());
            assertEquals("-", courses.stream()
                    .filter(course -> course.getCno().equals("t001"))
                    .findFirst().orElseThrow().getPrerequisiteText());

            // 服务端分页 / 搜索 / 余量筛选
            var firstPage = courseQueryService.pageForStudent(
                    "20200001", "", false, false, 1, 2);
            assertEquals(2, firstPage.getItems().size());
            assertEquals(5, firstPage.getTotalItems());
            assertEquals(3, firstPage.getTotalPages());

            var lastPage = courseQueryService.pageForStudent(
                    "20200001", "", false, false, 99, 2);
            assertEquals(1, lastPage.getItems().size());
            assertEquals(3, lastPage.getPage());

            var searched = courseQueryService.pageForStudent(
                    "20200001", "数据结构", false, false, 1, 10);
            assertEquals(1, searched.getTotalItems());
            assertEquals("t001", searched.getItems().getFirst().getCno());

            var availableOnly = courseQueryService.pageForStudent(
                    "20200001", "", true, false, 1, 10);
            assertEquals(4, availableOnly.getTotalItems());

            var adminPage = courseQueryService.pageAll("", 1, 2);
            assertEquals(5, adminPage.getTotalItems());
            assertEquals(3, adminPage.getTotalPages());

            EnrollmentService enrollmentService = new EnrollmentService(jdbc, 30);
            TransactionTemplate transaction = new TransactionTemplate(
                    new DataSourceTransactionManager(dataSource));

            assertEquals(EnrollmentOutcome.OK, transaction.execute(status ->
                    enrollmentService.enroll("20200001", "t001")));
            assertEquals(EnrollmentOutcome.ALREADY_SELECTED, transaction.execute(status ->
                    enrollmentService.enroll("20200001", "t001")));

            var selectedCourses = courseQueryService.listSelected("20200001");
            assertEquals(1, selectedCourses.size());
            assertEquals("t001", selectedCourses.getFirst().getCno());
            assertEquals(2, selectedCourses.getFirst().getCredits());
            assertEquals(EnrollmentOutcome.TIME_CONFLICT, transaction.execute(status ->
                    enrollmentService.enroll("20200001", "t003")));
            assertEquals(EnrollmentOutcome.PREREQUISITE_MISSING, transaction.execute(status ->
                    enrollmentService.enroll("20200001", "t005")));
            assertEquals(EnrollmentOutcome.FULL, transaction.execute(status ->
                    enrollmentService.enroll("20200001", "t004")));

            // 周二课程可以选
            assertEquals(EnrollmentOutcome.OK, transaction.execute(status ->
                    enrollmentService.enroll("20200001", "t002")));

            // 完成 t001 后，先修课 t005 可以选
            jdbc.update("insert into completed_courses (sno, cno) values (?, ?)",
                    "20200001", "t001");
            assertEquals(EnrollmentOutcome.OK, transaction.execute(status ->
                    enrollmentService.enroll("20200001", "t005")));

            // 退课后可以重新选
            assertTrue(Boolean.TRUE.equals(transaction.execute(status ->
                    enrollmentService.withdraw("20200001", "t002"))));
            assertEquals(EnrollmentOutcome.OK, transaction.execute(status ->
                    enrollmentService.enroll("20200001", "t002")));

            // 学分上限：max=2 时只能选一门 2 学分课程
            jdbc.update("""
                    insert into students (sno, name, grade, major, college, password_hash, phone)
                    values ('20200009', '学分测试学生', '2020级', '计算机', '信息学院', 'x', '13800000009')
                    """);
            EnrollmentService strictService = new EnrollmentService(jdbc, 2);
            assertEquals(EnrollmentOutcome.OK, transaction.execute(status ->
                    strictService.enroll("20200009", "t001")));
            assertEquals(EnrollmentOutcome.CREDIT_LIMIT_EXCEEDED, transaction.execute(status ->
                    strictService.enroll("20200009", "t002")));
        }
    }

    private void seed(JdbcTemplate jdbc) {
        jdbc.update("""
                insert into students (sno, name, grade, major, college, password_hash, phone)
                values ('20200001', '测试学生', '2020级', '计算机', '信息学院', 'x', '13800000000')
                """);
        jdbc.update("""
                insert into courses (cno, name, teacher, location, capacity, is_active)
                values
                  ('t001', '数据结构', '张老师', 'A101', 10, true),
                  ('t002', '操作系统', '李老师', 'B202', 10, true),
                  ('t003', '冲突课程', '王老师', 'C303', 10, true),
                  ('t004', '满员课程', '赵老师', 'D404', 0, true),
                  ('t005', '进阶课程', '钱老师', 'E505', 10, true)
                """);
        jdbc.update("""
                insert into course_sessions (cno, day_of_week, start_time, end_time)
                values
                  ('t001', 1, ?, ?),
                  ('t002', 2, ?, ?),
                  ('t003', 1, ?, ?),
                  ('t004', 3, ?, ?),
                  ('t005', 4, ?, ?)
                """, LocalTime.of(8, 30), LocalTime.of(10, 10),
                LocalTime.of(10, 20), LocalTime.of(12, 0),
                LocalTime.of(9, 0), LocalTime.of(10, 0),
                LocalTime.of(13, 0), LocalTime.of(15, 35),
                LocalTime.of(14, 50), LocalTime.of(17, 25));
        jdbc.update("insert into course_prerequisites (cno, prerequisite_cno) values ('t005', 't001')");
    }
}
