package com.example.choosecourse.service;

import com.example.choosecourse.web.dto.StudentRow;
import com.example.choosecourse.web.form.StudentForm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class StudentQueryService {

    private final JdbcTemplate jdbc;

    public StudentQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<StudentRow> listAll() {
        return jdbc.query("""
                select sno, name, grade, major, college, phone
                from students
                order by sno
                """, (rs, rowNum) -> new StudentRow(
                rs.getString("sno").trim(),
                rs.getString("name"),
                trim(rs.getString("grade")),
                trim(rs.getString("major")),
                trim(rs.getString("college")),
                trim(rs.getString("phone"))
        ));
    }

    public List<StudentRow> search(String q) {
        String keyword = q == null ? "" : q.trim();
        if (keyword.isEmpty()) {
            return listAll();
        }
        String pattern = "%" + keyword + "%";
        return jdbc.query("""
                select sno, name, grade, major, college, phone
                from students
                where sno ilike ? or name ilike ? or major ilike ? or grade ilike ?
                order by sno
                """, (rs, rowNum) -> new StudentRow(
                rs.getString("sno").trim(),
                rs.getString("name"),
                trim(rs.getString("grade")),
                trim(rs.getString("major")),
                trim(rs.getString("college")),
                trim(rs.getString("phone"))
        ), pattern, pattern, pattern, pattern);
    }

    public Optional<StudentForm> findForm(String sno) {
        List<StudentForm> rows = jdbc.query("""
                select sno, name, grade, major, college, phone
                from students
                where sno = ?
                """, (rs, rowNum) -> {
            StudentForm form = new StudentForm();
            form.setSno(rs.getString("sno").trim());
            form.setName(rs.getString("name"));
            form.setGrade(trim(rs.getString("grade")));
            form.setMajor(trim(rs.getString("major")));
            form.setCollege(trim(rs.getString("college")));
            form.setPhone(trim(rs.getString("phone")));
            return form;
        }, sno);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        StudentForm form = rows.getFirst();
        form.setCompletedCnos(jdbc.queryForList("""
                select cno from completed_courses where sno = ? order by cno
                """, String.class, sno));
        return Optional.of(form);
    }

    public Optional<StudentRow> findBySno(String sno) {
        List<StudentRow> rows = jdbc.query("""
                select sno, name, grade, major, college, phone
                from students
                where sno = ?
                """, (rs, rowNum) -> new StudentRow(
                rs.getString("sno").trim(),
                rs.getString("name"),
                trim(rs.getString("grade")),
                trim(rs.getString("major")),
                trim(rs.getString("college")),
                trim(rs.getString("phone"))
        ), sno);
        return rows.stream().findFirst();
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
