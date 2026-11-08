package com.example.choosecourse.service;

import com.example.choosecourse.web.form.StudentForm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class AdminStudentService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    public AdminStudentService(JdbcTemplate jdbc, PasswordEncoder passwordEncoder) {
        this.jdbc = jdbc;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public List<String> create(StudentForm form) {
        List<String> errors = new ArrayList<>();
        String sno = normalize(form.getSno());
        if (sno.isEmpty()) {
            errors.add("学号不能为空");
        } else if (existsStudent(sno)) {
            errors.add("学号已存在: " + sno);
        }
        if (normalize(form.getName()).isEmpty()) {
            errors.add("姓名不能为空");
        }
        validateCompletedCourses(form, errors);
        if (!errors.isEmpty()) {
            return errors;
        }

        String rawPassword = normalize(form.getPassword());
        if (rawPassword.isEmpty()) {
            rawPassword = "123456";
        }
        jdbc.update("""
                insert into students (sno, name, grade, major, college, password_hash, phone)
                values (?, ?, ?, ?, ?, ?, ?)
                """, sno, normalize(form.getName()), normalize(form.getGrade()),
                normalize(form.getMajor()), normalize(form.getCollege()),
                passwordEncoder.encode(rawPassword), normalize(form.getPhone()));
        replaceCompletedCourses(sno, form);
        return errors;
    }

    @Transactional
    public List<String> update(String sno, StudentForm form) {
        List<String> errors = new ArrayList<>();
        String normalizedSno = normalize(sno);
        if (!existsStudent(normalizedSno)) {
            errors.add("学生不存在: " + normalizedSno);
            return errors;
        }
        if (normalize(form.getName()).isEmpty()) {
            errors.add("姓名不能为空");
        }
        String rawPassword = normalize(form.getPassword());
        if (!rawPassword.isEmpty() && rawPassword.length() < 4) {
            errors.add("新密码至少 4 位；留空表示不修改");
        }
        validateCompletedCourses(form, errors);
        if (!errors.isEmpty()) {
            return errors;
        }

        jdbc.update("""
                update students
                set name = ?, grade = ?, major = ?, college = ?, phone = ?
                where sno = ?
                """, normalize(form.getName()), normalize(form.getGrade()),
                normalize(form.getMajor()), normalize(form.getCollege()),
                normalize(form.getPhone()), normalizedSno);
        if (!rawPassword.isEmpty()) {
            jdbc.update("update students set password_hash = ? where sno = ?",
                    passwordEncoder.encode(rawPassword), normalizedSno);
        }
        replaceCompletedCourses(normalizedSno, form);
        return errors;
    }

    @Transactional
    public List<String> delete(String sno) {
        List<String> errors = new ArrayList<>();
        if (!existsStudent(sno)) {
            errors.add("学生不存在: " + sno);
            return errors;
        }
        jdbc.update("delete from students where sno = ?", sno);
        return errors;
    }

    private void validateCompletedCourses(StudentForm form, List<String> errors) {
        for (String cno : normalizedCompletedCourses(form)) {
            if (!existsCourse(cno)) {
                errors.add("已修课程不存在: " + cno);
            }
        }
    }

    private void replaceCompletedCourses(String sno, StudentForm form) {
        jdbc.update("delete from completed_courses where sno = ?", sno);
        for (String cno : normalizedCompletedCourses(form)) {
            jdbc.update("""
                    insert into completed_courses (sno, cno) values (?, ?)
                    on conflict (sno, cno) do nothing
                    """, sno, cno);
        }
    }

    private boolean existsStudent(String sno) {
        return !jdbc.query("select 1 from students where sno = ?",
                (rs, rowNum) -> 1, sno).isEmpty();
    }

    private boolean existsCourse(String cno) {
        return !jdbc.query("select 1 from courses where cno = ?",
                (rs, rowNum) -> 1, cno).isEmpty();
    }

    private static List<String> normalizedCompletedCourses(StudentForm form) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (form.getCompletedCnos() != null) {
            for (String raw : form.getCompletedCnos()) {
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
