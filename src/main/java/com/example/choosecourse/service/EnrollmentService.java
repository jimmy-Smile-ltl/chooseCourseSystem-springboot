package com.example.choosecourse.service;

import com.example.choosecourse.web.dto.EnrollmentOutcome;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 选课/退课的唯一入口。
 *
 * 并发控制：
 * 1. 先锁学生行，保证同一个学生的并发请求串行执行；
 * 2. 再锁课程行，保证同一门课的容量检查串行执行；
 * 3. 唯一约束 (sno, cno) 作为最终兜底。
 */
@Service
public class EnrollmentService {

    private final JdbcTemplate jdbc;
    private final int maxCredits;

    public EnrollmentService(JdbcTemplate jdbc,
                             @Value("${app.max-credits:30}") int maxCredits) {
        this.jdbc = jdbc;
        this.maxCredits = maxCredits;
    }

    @Transactional
    public EnrollmentOutcome enroll(String sno, String cno) {
        // 1. 学生行锁：同一学生的并发选课请求串行化
        List<String> students = jdbc.queryForList(
                "select sno from students where sno = ? for update", String.class, sno);
        if (students.isEmpty()) {
            return EnrollmentOutcome.STUDENT_NOT_FOUND;
        }

        // 2. 课程行锁：同一门课的并发选课请求串行化
        List<CourseInfo> courses = jdbc.query(
                "select capacity, credits, is_active from courses where cno = ? for update",
                (rs, rowNum) -> new CourseInfo(
                        rs.getInt("capacity"),
                        rs.getBoolean("is_active"),
                        rs.getInt("credits")),
                cno);
        if (courses.isEmpty()) {
            return EnrollmentOutcome.COURSE_NOT_FOUND;
        }
        CourseInfo course = courses.getFirst();
        if (!course.active()) {
            return EnrollmentOutcome.COURSE_INACTIVE;
        }

        // 3. 业务校验
        if (exists("select 1 from completed_courses where sno = ? and cno = ?", sno, cno)) {
            return EnrollmentOutcome.ALREADY_COMPLETED;
        }

        Long unmetPrerequisites = jdbc.queryForObject("""
                select count(*)
                from course_prerequisites p
                where p.cno = ?
                  and not exists (
                      select 1
                      from completed_courses cc
                      where cc.sno = ? and cc.cno = p.prerequisite_cno
                  )
                """, Long.class, cno, sno);
        if (unmetPrerequisites != null && unmetPrerequisites > 0) {
            return EnrollmentOutcome.PREREQUISITE_MISSING;
        }

        if (exists("select 1 from enrollments where sno = ? and cno = ?", sno, cno)) {
            return EnrollmentOutcome.ALREADY_SELECTED;
        }

        Boolean conflict = jdbc.queryForObject("""
                select exists (
                    select 1
                    from enrollments e
                    join course_sessions es on es.cno = e.cno
                    join course_sessions ns on ns.cno = ?
                    where e.sno = ?
                      and es.day_of_week = ns.day_of_week
                      and es.start_time < ns.end_time
                      and ns.start_time < es.end_time
                )
                """, Boolean.class, cno, sno);
        if (Boolean.TRUE.equals(conflict)) {
            return EnrollmentOutcome.TIME_CONFLICT;
        }

        Long currentCredits = jdbc.queryForObject("""
                select coalesce(sum(c.credits), 0)
                from enrollments e
                join courses c on c.cno = e.cno
                where e.sno = ?
                """, Long.class, sno);
        if (currentCredits != null && currentCredits + course.credits() > maxCredits) {
            return EnrollmentOutcome.CREDIT_LIMIT_EXCEEDED;
        }

        Long selectedCount = jdbc.queryForObject(
                "select count(*) from enrollments where cno = ?", Long.class, cno);
        if (selectedCount != null && selectedCount >= course.capacity()) {
            return EnrollmentOutcome.FULL;
        }

        // 4. 唯一约束兜底；on conflict 避免 PostgreSQL 事务进入 aborted 状态
        int inserted = jdbc.update("""
                insert into enrollments (sno, cno)
                values (?, ?)
                on conflict (sno, cno) do nothing
                """, sno, cno);
        return inserted == 1 ? EnrollmentOutcome.OK : EnrollmentOutcome.ALREADY_SELECTED;
    }

    @Transactional
    public boolean withdraw(String sno, String cno) {
        return jdbc.update(
                "delete from enrollments where sno = ? and cno = ?", sno, cno) > 0;
    }

    private boolean exists(String sql, Object... args) {
        List<Integer> rows = jdbc.query(sql, (rs, rowNum) -> 1, args);
        return !rows.isEmpty();
    }

    private record CourseInfo(int capacity, boolean active, int credits) {
    }
}
