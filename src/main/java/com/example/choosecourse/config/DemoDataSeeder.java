package com.example.choosecourse.config;

import com.example.choosecourse.service.CourseCsvParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 本地演示数据。默认关闭，设置 APP_DEMO_SEED=true 开启。
 * 账号：只在 students 和 admins 都为空时写入，避免覆盖真实迁移数据。
 * 课程：读取 classpath:demo-courses.csv，按 cno 执行“存在则更新、不存在则插入”，
 *       并同步刷新上课时段和先修关系，方便演示库随着课程目录升级。
 *
 * 演示账号：
 *   学生 20200001 / 123456
 *   管理员 admin / admin123
 */
@Component
@ConditionalOnProperty(name = "app.demo-seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String CATALOG = "demo-courses.csv";
    private static final int MIN_COURSES = 100;

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final CourseCsvParser courseCsvParser;

    public DemoDataSeeder(JdbcTemplate jdbc, PasswordEncoder passwordEncoder,
                          CourseCsvParser courseCsvParser) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
        this.courseCsvParser = courseCsvParser;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean accountsSeeded = seedAccounts();
        SeedStats stats = seedCourses();

        log.info("演示数据就绪：课程新增 {} 门、更新 {} 门；账号{}",
                stats.inserted(), stats.updated(),
                accountsSeeded ? "已写入（20200001/123456, admin/admin123）" : "沿用现有");
    }

    private boolean seedAccounts() {
        Integer students = jdbc.queryForObject("select count(*) from students", Integer.class);
        Integer admins = jdbc.queryForObject("select count(*) from admins", Integer.class);
        if ((students != null && students > 0) || (admins != null && admins > 0)) {
            return false;
        }

        jdbc.update("""
                insert into admins (admin_id, name, password_hash, phone)
                values (?, ?, ?, ?)
                """, "admin", "演示管理员", passwordEncoder.encode("admin123"), "13900000000");

        jdbc.update("""
                insert into students (sno, name, grade, major, college, password_hash, phone)
                values (?, ?, ?, ?, ?, ?, ?)
                """, "20200001", "演示学生", "2024级", "计算机科学与技术",
                "信息学院", passwordEncoder.encode("123456"), "13800000000");
        return true;
    }

    /**
     * 演示课程目录由 CSV 统一维护：
     * 1. 先写课程主表，已存在则更新名称、教师、地点、容量、学分和开放状态；
     * 2. 每门课的上课时间整体替换，保证目录调整后课表同步；
     * 3. 全部课程落库后再写先修关系，避免前向引用触发外键错误。
     */
    private SeedStats seedCourses() {
        List<CourseCsvParser.ImportCourse> catalog = loadCatalog();
        Set<String> existingCnos = new HashSet<>(
                jdbc.queryForList("select cno from courses", String.class));

        List<Object[]> courseArgs = new ArrayList<>(catalog.size());
        int inserted = 0;
        int updated = 0;
        for (CourseCsvParser.ImportCourse course : catalog) {
            courseArgs.add(new Object[]{
                    course.cno(), course.name(), course.teacher(), course.location(),
                    course.capacity(), course.credits(), course.active()});
            if (existingCnos.contains(course.cno())) {
                updated++;
            } else {
                inserted++;
            }
        }
        jdbc.batchUpdate("""
                insert into courses (cno, name, teacher, location, capacity, credits, is_active)
                values (?, ?, ?, ?, ?, ?, ?)
                on conflict (cno) do update set
                    name = excluded.name,
                    teacher = excluded.teacher,
                    location = excluded.location,
                    capacity = excluded.capacity,
                    credits = excluded.credits,
                    is_active = excluded.is_active
                """, courseArgs);

        List<Object[]> cnoArgs = catalog.stream()
                .map(course -> new Object[]{course.cno()})
                .toList();
        jdbc.batchUpdate("delete from course_sessions where cno = ?", cnoArgs);

        List<Object[]> sessionArgs = new ArrayList<>();
        for (CourseCsvParser.ImportCourse course : catalog) {
            for (CourseCsvParser.Session session : course.sessions()) {
                sessionArgs.add(new Object[]{
                        course.cno(), session.dayOfWeek(), session.start(), session.end()});
            }
        }
        jdbc.batchUpdate("""
                insert into course_sessions (cno, day_of_week, start_time, end_time)
                values (?, ?, ?, ?)
                """, sessionArgs);

        jdbc.batchUpdate("delete from course_prerequisites where cno = ?", cnoArgs);

        List<Object[]> prerequisiteArgs = new ArrayList<>();
        for (CourseCsvParser.ImportCourse course : catalog) {
            for (String prerequisite : course.prerequisiteCnos()) {
                prerequisiteArgs.add(new Object[]{course.cno(), prerequisite});
            }
        }
        jdbc.batchUpdate("""
                insert into course_prerequisites (cno, prerequisite_cno)
                values (?, ?)
                """, prerequisiteArgs);

        Boolean demoStudentExists = jdbc.queryForObject(
                "select exists (select 1 from students where sno = ?)", Boolean.class, "20200001");
        if (Boolean.TRUE.equals(demoStudentExists)) {
            jdbc.update("""
                    insert into completed_courses (sno, cno)
                    values (?, ?)
                    on conflict do nothing
                    """, "20200001", "c000");
        }
        return new SeedStats(inserted, updated);
    }

    private List<CourseCsvParser.ImportCourse> loadCatalog() {
        ClassPathResource resource = new ClassPathResource(CATALOG);
        try (InputStream input = resource.getInputStream()) {
            CourseCsvParser.ParseResult parsed = courseCsvParser.parse(input.readAllBytes());
            if (!parsed.errors().isEmpty()) {
                String details = parsed.errors().stream().limit(5)
                        .map(error -> "第 " + error.getLine() + " 行：" + error.getMessage())
                        .collect(Collectors.joining("；"));
                throw new IllegalStateException("演示课程目录校验失败：" + details);
            }
            if (parsed.courses().size() < MIN_COURSES) {
                throw new IllegalStateException("演示课程目录至少需要 " + MIN_COURSES
                        + " 门课程，当前 " + parsed.courses().size() + " 门");
            }
            return parsed.courses();
        } catch (IOException e) {
            throw new IllegalStateException("无法读取演示课程目录 " + CATALOG, e);
        }
    }

    private record SeedStats(int inserted, int updated) {
    }
}
