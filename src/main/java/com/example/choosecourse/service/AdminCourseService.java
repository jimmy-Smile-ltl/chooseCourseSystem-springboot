package com.example.choosecourse.service;

import com.example.choosecourse.web.form.CourseForm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class AdminCourseService {

    private final JdbcTemplate jdbc;

    public AdminCourseService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public List<String> create(CourseForm form) {
        List<String> errors = new ArrayList<>();
        String cno = normalize(form.getCno());
        if (cno.isEmpty()) {
            errors.add("课程号不能为空");
        } else if (existsCourse(cno)) {
            errors.add("课程号已存在: " + cno);
        }
        validateCourse(form, cno, errors);
        if (!errors.isEmpty()) {
            return errors;
        }

        jdbc.update("""
                insert into courses (cno, name, teacher, location, capacity, credits, is_active)
                values (?, ?, ?, ?, ?, ?, ?)
                """, cno, normalize(form.getName()), normalize(form.getTeacher()),
                normalize(form.getLocation()), form.getCapacity(), form.getCredits(),
                form.isActive());
        replaceSessionsAndPrerequisites(cno, form);
        return errors;
    }

    @Transactional
    public List<String> update(String cno, CourseForm form) {
        List<String> errors = new ArrayList<>();
        String normalizedCno = normalize(cno);
        if (!existsCourse(normalizedCno)) {
            errors.add("课程不存在: " + normalizedCno);
            return errors;
        }
        validateCourse(form, normalizedCno, errors);

        Long enrolled = jdbc.queryForObject(
                "select count(*) from enrollments where cno = ?", Long.class, normalizedCno);
        if (enrolled != null && enrolled > form.getCapacity()) {
            errors.add("容量不能小于当前已选人数（当前已选 " + enrolled + " 人）");
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        jdbc.update("""
                update courses
                set name = ?, teacher = ?, location = ?, capacity = ?, credits = ?, is_active = ?
                where cno = ?
                """, normalize(form.getName()), normalize(form.getTeacher()),
                normalize(form.getLocation()), form.getCapacity(), form.getCredits(),
                form.isActive(), normalizedCno);
        replaceSessionsAndPrerequisites(normalizedCno, form);
        return errors;
    }

    @Transactional
    public boolean toggleActive(String cno) {
        return jdbc.update("""
                update courses set is_active = not is_active where cno = ?
                """, cno) == 1;
    }

    @Transactional
    public List<String> delete(String cno) {
        List<String> errors = new ArrayList<>();
        if (!existsCourse(cno)) {
            errors.add("课程不存在: " + cno);
            return errors;
        }

        Long enrolled = jdbc.queryForObject(
                "select count(*) from enrollments where cno = ?", Long.class, cno);
        Long completed = jdbc.queryForObject(
                "select count(*) from completed_courses where cno = ?", Long.class, cno);
        Long referenced = jdbc.queryForObject(
                "select count(*) from course_prerequisites where prerequisite_cno = ?", Long.class, cno);

        if (enrolled != null && enrolled > 0) {
            errors.add("该课程已有选课记录，不能删除；可以改为停开。");
        }
        if (completed != null && completed > 0) {
            errors.add("该课程已有已修记录，不能删除；可以改为停开。");
        }
        if (referenced != null && referenced > 0) {
            errors.add("该课程被其他课程作为先修课引用，不能删除；可以改为停开。");
        }
        if (!errors.isEmpty()) {
            return errors;
        }

        jdbc.update("delete from courses where cno = ?", cno);
        return errors;
    }

    private void validateCourse(CourseForm form, String cno, List<String> errors) {
        if (normalize(form.getName()).isEmpty()) {
            errors.add("课程名称不能为空");
        }
        if (form.getCapacity() < 0) {
            errors.add("容量不能小于 0");
        }
        if (form.getCredits() < 0 || form.getCredits() > 12) {
            errors.add("学分必须在 0-12 之间");
        }
        Integer day = form.getDayOfWeek();
        if (day == null || day < 1 || day > 7) {
            errors.add("上课星期必须在 1-7 之间");
        }
        LocalTime start = form.getStartTime();
        LocalTime end = form.getEndTime();
        if (start == null || end == null) {
            errors.add("上课开始/结束时间不能为空");
        } else if (!start.isBefore(end)) {
            errors.add("上课开始时间必须早于结束时间");
        }

        for (String prerequisite : normalizedPrerequisites(form)) {
            if (!cno.isEmpty() && prerequisite.equals(cno)) {
                errors.add("先修课不能是课程本身");
            } else if (!existsCourse(prerequisite)) {
                errors.add("先修课不存在: " + prerequisite);
            }
        }
    }

    private void replaceSessionsAndPrerequisites(String cno, CourseForm form) {
        jdbc.update("delete from course_sessions where cno = ?", cno);
        jdbc.update("""
                insert into course_sessions (cno, day_of_week, start_time, end_time)
                values (?, ?, ?, ?)
                """, cno, form.getDayOfWeek(), form.getStartTime(), form.getEndTime());

        jdbc.update("delete from course_prerequisites where cno = ?", cno);
        for (String prerequisite : normalizedPrerequisites(form)) {
            jdbc.update("""
                    insert into course_prerequisites (cno, prerequisite_cno)
                    values (?, ?)
                    """, cno, prerequisite);
        }
    }

    private boolean existsCourse(String cno) {
        List<Integer> rows = jdbc.query(
                "select 1 from courses where cno = ?", (rs, rowNum) -> 1, cno);
        return !rows.isEmpty();
    }

    private static List<String> normalizedPrerequisites(CourseForm form) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (form.getPrerequisiteCnos() != null) {
            for (String raw : form.getPrerequisiteCnos()) {
                if (raw == null) {
                    continue;
                }
                String code = raw.trim();
                if (!code.isEmpty()) {
                    result.add(code);
                }
            }
        }
        return new ArrayList<>(result);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
