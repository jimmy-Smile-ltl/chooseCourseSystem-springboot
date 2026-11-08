package com.example.choosecourse.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;

/**
 * 本地演示数据。默认关闭，设置 APP_DEMO_SEED=true 开启。
 * 账号：只在 students 和 admins 都为空时写入，避免覆盖真实迁移数据。
 * 课程：按 cno 幂等补齐，已有课程（可能是真实数据）不会被修改或追加时段。
 *
 * 演示账号：
 *   学生 20200001 / 123456
 *   管理员 admin / admin123
 */
@Component
@ConditionalOnProperty(name = "app.demo-seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public DemoDataSeeder(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        boolean accountsSeeded = seedAccounts();
        int coursesAdded = seedCourses();

        log.info("演示数据就绪：新增 {} 门课程；账号{}",
                coursesAdded, accountsSeeded ? "已写入（20200001/123456, admin/admin123）" : "沿用现有");
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
     * 33 门演示课程，覆盖专业课、公共基础课、通识课和体育课；
     * 部分课程一周两次（m001/m003/l001），用来展示跨时段课表。
     */
    private int seedCourses() {
        int added = 0;

        // 已修课程，用来展示“已修”状态
        added += insertCourse("c000", "程序设计基础", "何老师", "A100", 80, 3, null,
                s(2, "08:30", "10:10"));

        added += insertCourse("t001", "数据结构", "张老师", "A101", 60, 4, null,
                s(1, "08:30", "10:10"));
        added += insertCourse("t002", "操作系统", "李老师", "B202", 45, 4, "t001",
                s(2, "10:20", "12:00"));
        added += insertCourse("t003", "Web 应用开发", "王老师", "C303", 40, 3, null,
                s(3, "13:00", "15:35"));
        added += insertCourse("t004", "计算机网络", "周老师", "B404", 50, 3, null,
                s(1, "09:25", "11:05"));
        added += insertCourse("t005", "数据库系统", "刘老师", "A102", 50, 3, "t001",
                s(3, "08:30", "10:10"));
        added += insertCourse("t006", "计算机组成原理", "孙老师", "B203", 55, 4, "c000",
                s(4, "10:20", "12:00"));
        added += insertCourse("t007", "软件工程", "郑老师", "C304", 45, 3, null,
                s(5, "08:30", "10:10"));
        added += insertCourse("t008", "人工智能导论", "吴老师", "A201", 40, 3, "c000",
                s(2, "13:00", "15:35"));
        added += insertCourse("t009", "机器学习", "冯老师", "A202", 35, 3, "t008",
                s(5, "10:20", "12:00"));
        added += insertCourse("t010", "算法设计与分析", "陈老师", "B301", 50, 3, "t001",
                s(4, "13:00", "15:35"));
        added += insertCourse("t011", "离散数学", "蒋老师", "B302", 60, 3, null,
                s(1, "13:00", "15:35"));
        added += insertCourse("t012", "编译原理", "沈老师", "B305", 40, 3, "t001",
                s(3, "10:20", "12:00"));
        added += insertCourse("t013", "网络安全基础", "韩老师", "C305", 45, 3, null,
                s(5, "13:00", "15:35"));
        added += insertCourse("t014", "移动应用开发", "杨老师", "C306", 40, 2, null,
                s(2, "15:45", "17:25"));

        added += insertCourse("m001", "高等数学（一）", "罗老师", "A301", 100, 5, null,
                s(2, "08:30", "10:10"), s(4, "08:30", "10:10"));
        added += insertCourse("m002", "线性代数", "马老师", "A302", 80, 3, null,
                s(3, "13:00", "15:35"));
        added += insertCourse("m003", "大学物理（上）", "高老师", "B401", 90, 4, null,
                s(1, "10:20", "12:00"), s(5, "10:20", "12:00"));
        added += insertCourse("m004", "概率论与数理统计", "顾老师", "A303", 70, 3, "m001",
                s(4, "15:45", "17:25"));

        added += insertCourse("l001", "大学英语（三）", "林老师", "D101", 45, 3, null,
                s(1, "08:30", "10:10"), s(3, "08:30", "10:10"));
        added += insertCourse("l002", "学术英语写作", "唐老师", "D102", 30, 2, null,
                s(2, "18:30", "20:10"));
        added += insertCourse("l003", "中国近现代史纲要", "邓老师", "F101", 120, 2, null,
                s(3, "18:30", "21:05"));
        added += insertCourse("l004", "心理学导论", "苏老师", "F102", 80, 2, null,
                s(4, "19:25", "21:05"));
        added += insertCourse("l005", "创新创业基础", "曾老师", "F103", 60, 2, null,
                s(6, "13:00", "15:35"));

        added += insertCourse("e001", "微观经济学", "黄老师", "E101", 80, 3, null,
                s(3, "15:45", "17:25"));
        added += insertCourse("e002", "金融学基础", "钱老师", "E102", 60, 3, null,
                s(5, "18:30", "21:05"));

        added += insertCourse("a001", "音乐鉴赏", "宋老师", "艺术楼101", 120, 1, null,
                s(2, "19:25", "21:05"));
        added += insertCourse("a002", "影视艺术赏析", "秦老师", "艺术楼102", 100, 1, null,
                s(4, "18:30", "21:05"));

        added += insertCourse("pe001", "篮球", "赵老师", "体育馆", 30, 1, null,
                s(5, "15:45", "17:25"));
        added += insertCourse("pe002", "羽毛球", "许老师", "体育馆", 24, 1, null,
                s(6, "08:30", "10:10"));
        added += insertCourse("pe003", "太极拳", "武老师", "体育馆", 40, 1, null,
                s(6, "10:20", "12:00"));

        added += insertCourse("hs001", "大学生心理健康", "陈老师", "E-501", 200, 2, null,
                s(4, "18:30", "21:05"));
        added += insertCourse("h001", "急救常识", "曹老师", "校医院201", 60, 1, null,
                s(7, "08:30", "10:10"));

        Boolean demoStudentExists = jdbc.queryForObject(
                "select exists (select 1 from students where sno = ?)", Boolean.class, "20200001");
        if (Boolean.TRUE.equals(demoStudentExists)) {
            jdbc.update("""
                    insert into completed_courses (sno, cno)
                    values (?, ?)
                    on conflict do nothing
                    """, "20200001", "c000");
        }
        return added;
    }

    /**
     * 幂等插入：课程已存在时直接跳过，不修改真实课程，也不追加时段。
     */
    private int insertCourse(String cno, String name, String teacher, String location,
                             int capacity, int credits, String prerequisiteCno,
                             Session... sessions) {
        int inserted = jdbc.update("""
                insert into courses (cno, name, teacher, location, capacity, credits, is_active)
                values (?, ?, ?, ?, ?, ?, true)
                on conflict (cno) do nothing
                """, cno, name, teacher, location, capacity, credits);
        if (inserted == 0) {
            return 0;
        }
        for (Session session : sessions) {
            jdbc.update("""
                    insert into course_sessions (cno, day_of_week, start_time, end_time)
                    values (?, ?, ?, ?)
                    on conflict (cno, day_of_week, start_time) do nothing
                    """, cno, session.dayOfWeek(), session.start(), session.end());
        }
        if (prerequisiteCno != null) {
            jdbc.update("""
                    insert into course_prerequisites (cno, prerequisite_cno)
                    values (?, ?)
                    on conflict do nothing
                    """, cno, prerequisiteCno);
        }
        return 1;
    }

    private static Session s(int dayOfWeek, String start, String end) {
        return new Session(dayOfWeek, LocalTime.parse(start), LocalTime.parse(end));
    }

    private record Session(int dayOfWeek, LocalTime start, LocalTime end) {
    }
}
