package com.example.choosecourse.config;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 演示种子必须幂等：多次启动不能重复加课、重复加时段或重复标记已修课程。
 */
class DemoDataSeederTest {

    @Test
    void seedingIsIdempotentAndBuildsFullCatalog() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            DemoDataSeeder seeder = new DemoDataSeeder(jdbc, new BCryptPasswordEncoder());

            seeder.run(null);
            seeder.run(null);

            assertEquals(33, jdbc.queryForObject(
                    "select count(*) from courses", Integer.class));
            assertEquals(36, jdbc.queryForObject(
                    "select count(*) from course_sessions", Integer.class));
            assertEquals(8, jdbc.queryForObject(
                    "select count(*) from course_prerequisites", Integer.class));
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from completed_courses where sno = '20200001' and cno = 'c000'",
                    Integer.class));
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from students", Integer.class));
            assertEquals(1, jdbc.queryForObject(
                    "select count(*) from admins", Integer.class));
        }
    }
}
