package com.example.choosecourse.service;

import com.example.choosecourse.web.dto.CourseImportError;
import com.example.choosecourse.web.dto.CourseImportResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 课程批量导入：解析校验通过后，整批写入同一个事务。
 * 已存在的课程号、缺失的先修课都会在写库前被拒绝。
 */
@Service
public class CourseImportService {

    private final JdbcTemplate jdbc;
    private final CourseCsvParser parser;

    public CourseImportService(JdbcTemplate jdbc, CourseCsvParser parser) {
        this.jdbc = jdbc;
        this.parser = parser;
    }

    @Transactional(rollbackFor = Exception.class)
    public CourseImportResult importCsv(byte[] content) {
        CourseCsvParser.ParseResult parsed = parser.parse(content);
        if (!parsed.errors().isEmpty()) {
            return new CourseImportResult(parsed.errors(), List.of(), 0, 0);
        }

        List<CourseCsvParser.ImportCourse> courses = parsed.courses();
        Set<String> importedCnos = new HashSet<>();
        for (CourseCsvParser.ImportCourse course : courses) {
            importedCnos.add(course.cno());
        }

        List<CourseImportError> errors = new ArrayList<>();
        for (CourseCsvParser.ImportCourse course : courses) {
            if (existsCourse(course.cno())) {
                errors.add(new CourseImportError(course.line(),
                        "课程号已存在：" + course.cno()));
            }
            for (String prerequisite : course.prerequisiteCnos()) {
                if (!importedCnos.contains(prerequisite) && !existsCourse(prerequisite)) {
                    errors.add(new CourseImportError(course.line(),
                            "先修课程不存在：" + prerequisite));
                }
            }
        }
        if (!errors.isEmpty()) {
            return new CourseImportResult(errors, List.of(), 0, 0);
        }

        // 先写课程，再写时段和先修关系，避免先修课尚未插入时触发外键错误。
        for (CourseCsvParser.ImportCourse course : courses) {
            jdbc.update("""
                    insert into courses (cno, name, teacher, location, capacity, credits, is_active)
                    values (?, ?, ?, ?, ?, ?, ?)
                    """, course.cno(), course.name(), course.teacher(), course.location(),
                    course.capacity(), course.credits(), course.active());
        }

        int sessionCount = 0;
        for (CourseCsvParser.ImportCourse course : courses) {
            for (CourseCsvParser.Session session : course.sessions()) {
                jdbc.update("""
                        insert into course_sessions (cno, day_of_week, start_time, end_time)
                        values (?, ?, ?, ?)
                        """, course.cno(), session.dayOfWeek(), session.start(), session.end());
                sessionCount++;
            }
            for (String prerequisite : course.prerequisiteCnos()) {
                jdbc.update("""
                        insert into course_prerequisites (cno, prerequisite_cno)
                        values (?, ?)
                        """, course.cno(), prerequisite);
            }
        }

        return new CourseImportResult(List.of(), List.of(), courses.size(), sessionCount);
    }

    private boolean existsCourse(String cno) {
        List<Integer> rows = jdbc.query(
                "select 1 from courses where cno = ?", (rs, rowNum) -> 1, cno);
        return !rows.isEmpty();
    }
}
