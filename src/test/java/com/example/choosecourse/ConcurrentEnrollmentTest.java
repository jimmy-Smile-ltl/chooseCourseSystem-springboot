package com.example.choosecourse;

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
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 并发抢最后一个名额：两个学生同时选容量为 1 的课程，只能有一个成功。
 */
class ConcurrentEnrollmentTest {

    @Test
    void onlyOneStudentGetsTheLastSeat() throws Exception {
        try (EmbeddedPostgres postgres = EmbeddedPostgres.start()) {
            DataSource dataSource = postgres.getPostgresDatabase();
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            seed(jdbc);

            EnrollmentService enrollmentService = new EnrollmentService(jdbc, 30);
            TransactionTemplate transaction = new TransactionTemplate(
                    new DataSourceTransactionManager(dataSource));

            ExecutorService executor = Executors.newFixedThreadPool(2);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);

            try {
                Future<EnrollmentOutcome> first = executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start timeout");
                    }
                    return transaction.execute(status ->
                            enrollmentService.enroll("20200001", "t001"));
                });
                Future<EnrollmentOutcome> second = executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("start timeout");
                    }
                    return transaction.execute(status ->
                            enrollmentService.enroll("20200002", "t001"));
                });

                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();

                List<EnrollmentOutcome> results = List.of(
                        Objects.requireNonNull(first.get(20, TimeUnit.SECONDS)),
                        Objects.requireNonNull(second.get(20, TimeUnit.SECONDS))
                );

                assertEquals(1, results.stream()
                        .filter(result -> result == EnrollmentOutcome.OK)
                        .count());
                assertEquals(1, results.stream()
                        .filter(result -> result == EnrollmentOutcome.FULL)
                        .count());
                assertEquals(1, jdbc.queryForObject(
                        "select count(*) from enrollments where cno = 't001'", Integer.class));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    private void seed(JdbcTemplate jdbc) {
        jdbc.update("""
                insert into students (sno, name, grade, major, college, password_hash, phone)
                values
                  ('20200001', '学生一', '2020级', '计算机', '信息学院', 'x', '13800000001'),
                  ('20200002', '学生二', '2020级', '计算机', '信息学院', 'x', '13800000002')
                """);
        jdbc.update("""
                insert into courses (cno, name, teacher, location, capacity, is_active)
                values ('t001', '只有一个名额的课', '张老师', 'A101', 1, true)
                """);
        jdbc.update("""
                insert into course_sessions (cno, day_of_week, start_time, end_time)
                values ('t001', 1, ?, ?)
                """, LocalTime.of(8, 30), LocalTime.of(10, 10));
    }
}
