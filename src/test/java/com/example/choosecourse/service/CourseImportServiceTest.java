package com.example.choosecourse.service;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseImportServiceTest {

    private static final String HEADER =
            "课程号,课程名称,教师,地点,容量,学分,星期,开始时间,结束时间,先修课程号,是否开放\r\n";

    @Test
    void importsValidCsvAndRejectsDuplicatesAndMissingPrerequisites() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            CourseImportService service = new CourseImportService(jdbc, new CourseCsvParser());

            String valid = HEADER
                    + "cs101,数据结构,张老师,A101,60,4,周一,08:30,10:10,,是\r\n"
                    + "cs101,数据结构,张老师,A101,60,4,周三,08:30,10:10,,是\r\n"
                    + "cs102,操作系统,李老师,B202,40,3,周二,13:00,15:35,cs101,否\r\n";

            var result = service.importCsv(valid.getBytes(StandardCharsets.UTF_8));
            assertTrue(result.getErrors().isEmpty(), () -> result.getErrors().toString());
            assertEquals(2, result.getCoursesImported());
            assertEquals(3, result.getSessionsImported());
            assertEquals(2, jdbc.queryForObject("select count(*) from courses", Integer.class));
            assertEquals(3, jdbc.queryForObject("select count(*) from course_sessions", Integer.class));
            assertEquals(1, jdbc.queryForObject("select count(*) from course_prerequisites", Integer.class));

            var duplicate = service.importCsv(valid.getBytes(StandardCharsets.UTF_8));
            assertFalse(duplicate.getErrors().isEmpty());
            assertTrue(duplicate.getErrors().stream()
                    .anyMatch(error -> error.getMessage().contains("课程号已存在")));
            assertEquals(2, jdbc.queryForObject("select count(*) from courses", Integer.class));

            String missingPrerequisite = HEADER
                    + "cs103,算法,王老师,C303,30,3,周四,13:00,15:35,not_exist,是\r\n";
            var missing = service.importCsv(missingPrerequisite.getBytes(StandardCharsets.UTF_8));
            assertFalse(missing.getErrors().isEmpty());
            assertTrue(missing.getErrors().stream()
                    .anyMatch(error -> error.getMessage().contains("先修课程不存在")));
            assertEquals(2, jdbc.queryForObject("select count(*) from courses", Integer.class));
        }
    }

    @Test
    void parserErrorsInsertNothing() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            CourseImportService service = new CourseImportService(jdbc, new CourseCsvParser());

            String invalid = HEADER + "cs101,课程,张老师,A101,60,99,周一,08:30,10:10,,是\r\n";
            var result = service.importCsv(invalid.getBytes(StandardCharsets.UTF_8));

            assertFalse(result.getErrors().isEmpty());
            assertEquals(0, jdbc.queryForObject("select count(*) from courses", Integer.class));
        }
    }
}
