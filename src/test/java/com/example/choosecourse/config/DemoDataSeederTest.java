package com.example.choosecourse.config;

import com.example.choosecourse.service.CourseCsvParser;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 演示种子必须幂等：多次启动不能重复加课、重复加时段或重复标记已修课程；
 * 课程目录升级后，已有课程应被更新，新课应被插入。
 */
class DemoDataSeederTest {

    @Test
    void seedingBuildsLargeCatalogIsIdempotentAndUpdatesExistingCourses() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            DemoDataSeeder seeder = new DemoDataSeeder(
                    jdbc, new BCryptPasswordEncoder(), new CourseCsvParser());

            seeder.run(null);

            int courses = count(jdbc, "select count(*) from courses");
            int sessions = count(jdbc, "select count(*) from course_sessions");
            int prerequisites = count(jdbc, "select count(*) from course_prerequisites");
            assertTrue(courses >= 100, "演示课程应不少于 100 门，实际 " + courses);
            assertTrue(sessions > courses, "多时段课程应产生多于课程数的时段");
            assertTrue(prerequisites >= 8, "应包含先修课关系");
            assertEquals(1, count(jdbc, "select count(*) from completed_courses "
                    + "where sno = '20200001' and cno = 'c000'"));
            assertEquals(1, count(jdbc, "select count(*) from students"));
            assertEquals(1, count(jdbc, "select count(*) from admins"));

            seeder.run(null);

            assertEquals(courses, count(jdbc, "select count(*) from courses"));
            assertEquals(sessions, count(jdbc, "select count(*) from course_sessions"));
            assertEquals(prerequisites, count(jdbc, "select count(*) from course_prerequisites"));
            assertEquals(1, count(jdbc, "select count(*) from completed_courses "
                    + "where sno = '20200001' and cno = 'c000'"));

            // 已有课程被改脏、时段和先修关系被删除后，再次播种应恢复目录内容。
            jdbc.update("update courses set name = '旧名称' where cno = 'm001'");
            jdbc.update("delete from course_sessions where cno = 'm001'");
            jdbc.update("delete from course_prerequisites where cno = 't002'");

            seeder.run(null);

            assertEquals("高等数学（一）", jdbc.queryForObject(
                    "select name from courses where cno = 'm001'", String.class));
            assertEquals(2, count(jdbc, "select count(*) from course_sessions where cno = 'm001'"));
            assertEquals(1, count(jdbc, "select count(*) from course_prerequisites "
                    + "where cno = 't002' and prerequisite_cno = 't001'"));
        }
    }

    private static int count(JdbcTemplate jdbc, String sql) {
        Integer value = jdbc.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
