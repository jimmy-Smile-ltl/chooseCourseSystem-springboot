package com.example.choosecourse.service;

import com.example.choosecourse.web.dto.CourseRow;
import com.example.choosecourse.web.dto.PageResult;
import com.example.choosecourse.web.dto.ScheduleEntry;
import com.example.choosecourse.web.dto.StudentRow;
import com.example.choosecourse.web.form.CourseForm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CourseQueryService {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String BASE_SELECT = """
            select c.cno, c.name, c.teacher, c.location, c.capacity, c.credits, c.is_active,
                   coalesce(x.selected_count, 0) as selected_count,
                   greatest(c.capacity - coalesce(x.selected_count, 0), 0) as remaining_seats,
                   coalesce(t.time_text, '') as time_text,
                   coalesce(p.pre_text, '-') as pre_text,
                   (e.id is not null) as selected,
                   (cc.sno is not null) as completed,
                   (exists (
                       select 1
                       from course_prerequisites cp
                       where cp.cno = c.cno
                         and not exists (
                             select 1
                             from completed_courses ccc
                             where ccc.sno = ? and ccc.cno = cp.prerequisite_cno
                         )
                   )) as prerequisite_missing,
                   (exists (
                       select 1
                       from enrollments e2
                       join course_sessions s1 on s1.cno = e2.cno
                       join course_sessions s2 on s2.cno = c.cno
                       where e2.sno = ?
                         and e2.cno <> c.cno
                         and s1.day_of_week = s2.day_of_week
                         and s1.start_time < s2.end_time
                         and s2.start_time < s1.end_time
                   )) as conflict
            from courses c
            left join (
                select cno, count(*)::int as selected_count
                from enrollments
                group by cno
            ) x on x.cno = c.cno
            left join (
                select cno,
                       string_agg(
                           (case day_of_week
                                when 1 then '周一' when 2 then '周二' when 3 then '周三'
                                when 4 then '周四' when 5 then '周五' when 6 then '周六'
                                else '周日' end)
                           || ' ' || substring(start_time::text from 1 for 5)
                           || '-' || substring(end_time::text from 1 for 5),
                           ' / ' order by day_of_week, start_time
                       ) as time_text
                from course_sessions
                group by cno
            ) t on t.cno = c.cno
            left join (
                select cno,
                       string_agg(prerequisite_cno, '、' order by prerequisite_cno) as pre_text
                from course_prerequisites
                group by cno
            ) p on p.cno = c.cno
            left join enrollments e on e.cno = c.cno and e.sno = ?
            left join completed_courses cc on cc.cno = c.cno and cc.sno = ?
            """;

    private static final RowMapper<CourseRow> MAPPER = (rs, rowNum) -> new CourseRow(
            rs.getString("cno").trim(),
            rs.getString("name"),
            trim(rs.getString("teacher")),
            trim(rs.getString("location")),
            rs.getInt("capacity"),
            rs.getInt("credits"),
            rs.getInt("selected_count"),
            rs.getInt("remaining_seats"),
            rs.getString("time_text"),
            rs.getString("pre_text"),
            rs.getBoolean("is_active"),
            rs.getBoolean("selected"),
            rs.getBoolean("completed"),
            rs.getBoolean("prerequisite_missing"),
            rs.getBoolean("conflict")
    );

    private final JdbcTemplate jdbc;

    public CourseQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 学生选课页：只显示可选（is_active=true）的课程，并带上
     * 已选/已修/先修未满足/与当前课表冲突/余量 状态。
     */
    public List<CourseRow> listForStudent(String sno) {
        return jdbc.query(BASE_SELECT + " where c.is_active order by c.cno",
                MAPPER, sno, sno, sno, sno);
    }

    /**
     * 学生已选课程：不受 is_active 影响，停开课程也会出现在已选清单里，
     * 保证学分统计与真实选课记录一致。
     */
    public List<CourseRow> listSelected(String sno) {
        return jdbc.query(BASE_SELECT + " where e.id is not null order by c.cno",
                MAPPER, sno, sno, sno, sno);
    }

    /**
     * 学生可选课程（服务端分页）：关键字、余量、冲突筛选都在数据库中完成。
     */
    public PageResult<CourseRow> pageForStudent(String sno, String q,
                                                boolean onlyAvailable, boolean onlyNoConflict,
                                                int page, int pageSize) {
        String keyword = q == null ? "" : q.trim();
        List<Object> args = new ArrayList<>(List.of(sno, sno, sno, sno));
        StringBuilder where = new StringBuilder(" where q.is_active");
        appendKeywordAndFilters(where, args, keyword, onlyAvailable, onlyNoConflict);
        return pageQuery(where.toString(), args, page, pageSize);
    }

    /**
     * 管理端课程列表（服务端分页）：包含已停开课程。
     */
    public PageResult<CourseRow> pageAll(String q, int page, int pageSize) {
        String keyword = q == null ? "" : q.trim();
        List<Object> args = new ArrayList<>(List.of("", "", "", ""));
        StringBuilder where = new StringBuilder(" where 1 = 1");
        appendKeywordAndFilters(where, args, keyword, false, false);
        return pageQuery(where.toString(), args, page, pageSize);
    }

    /**
     * 管理端课程列表：包含已停开课程（下拉选项等小场景使用）。
     */
    public List<CourseRow> listAll() {
        return jdbc.query(BASE_SELECT + " order by c.cno", MAPPER, "", "", "", "");
    }

    public List<CourseRow> search(String q) {
        String keyword = q == null ? "" : q.trim();
        if (keyword.isEmpty()) {
            return listAll();
        }
        String pattern = "%" + keyword + "%";
        return jdbc.query(BASE_SELECT + """
                 where c.cno ilike ? or c.name ilike ? or c.teacher ilike ?
                 order by c.cno
                """, MAPPER, "", "", "", "", pattern, pattern, pattern);
    }

    private void appendKeywordAndFilters(StringBuilder where, List<Object> args,
                                         String keyword,
                                         boolean onlyAvailable, boolean onlyNoConflict) {
        if (!keyword.isEmpty()) {
            where.append(" and (q.cno ilike ? or q.name ilike ? or q.teacher ilike ?)");
            String pattern = "%" + keyword + "%";
            args.add(pattern);
            args.add(pattern);
            args.add(pattern);
        }
        if (onlyAvailable) {
            where.append(" and q.remaining_seats > 0");
        }
        if (onlyNoConflict) {
            where.append(" and q.conflict = false");
        }
    }

    private PageResult<CourseRow> pageQuery(String where, List<Object> args,
                                            int page, int pageSize) {
        int size = Math.max(1, Math.min(pageSize, 100));
        Long total = jdbc.queryForObject(
                "select count(*) from (" + BASE_SELECT + ") q" + where,
                Long.class, args.toArray());
        int totalItems = total == null ? 0 : total.intValue();
        int totalPages = Math.max(1,
                (int) Math.ceil(totalItems / (double) size));
        int safePage = Math.min(Math.max(1, page), totalPages);

        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((safePage - 1) * size);
        List<CourseRow> rows = jdbc.query(
                "select * from (" + BASE_SELECT + ") q" + where
                        + " order by q.cno limit ? offset ?",
                MAPPER, pageArgs.toArray());
        return new PageResult<>(rows, safePage, size, totalItems);
    }

    /**
     * 管理端编辑表单：目前编辑页只编辑第一个上课时段。
     * 数据库仍然支持一门课多个时段，后续可扩展成动态时段列表。
     */
    public Optional<CourseForm> findForm(String cno) {
        List<CourseForm> rows = jdbc.query("""
                select cno, name, teacher, location, capacity, credits, is_active
                from courses
                where cno = ?
                """, (rs, rowNum) -> {
            CourseForm form = new CourseForm();
            form.setCno(rs.getString("cno").trim());
            form.setName(rs.getString("name"));
            form.setTeacher(trim(rs.getString("teacher")));
            form.setLocation(trim(rs.getString("location")));
            form.setCapacity(rs.getInt("capacity"));
            form.setCredits(rs.getInt("credits"));
            form.setActive(rs.getBoolean("is_active"));
            return form;
        }, cno);
        if (rows.isEmpty()) {
            return Optional.empty();
        }

        CourseForm form = rows.getFirst();
        List<CourseForm> sessions = jdbc.query("""
                select day_of_week, start_time, end_time
                from course_sessions
                where cno = ?
                order by day_of_week, start_time
                limit 1
                """, (rs, rowNum) -> {
            CourseForm session = new CourseForm();
            session.setDayOfWeek(rs.getInt("day_of_week"));
            session.setStartTime(rs.getTime("start_time").toLocalTime());
            session.setEndTime(rs.getTime("end_time").toLocalTime());
            return session;
        }, cno);
        if (!sessions.isEmpty()) {
            CourseForm session = sessions.getFirst();
            form.setDayOfWeek(session.getDayOfWeek());
            form.setStartTime(session.getStartTime());
            form.setEndTime(session.getEndTime());
        }

        List<String> prerequisites = jdbc.queryForList("""
                select prerequisite_cno
                from course_prerequisites
                where cno = ?
                order by prerequisite_cno
                """, String.class, cno);
        form.setPrerequisiteCnos(prerequisites);
        return Optional.of(form);
    }

    public List<ScheduleEntry> listSchedule(String sno) {
        return jdbc.query("""
                select c.cno, c.name, c.teacher, c.location,
                       cs.day_of_week, cs.start_time, cs.end_time
                from enrollments e
                join courses c on c.cno = e.cno
                join course_sessions cs on cs.cno = c.cno
                where e.sno = ?
                order by cs.day_of_week, cs.start_time
                """, (rs, rowNum) -> new ScheduleEntry(
                rs.getString("cno").trim(),
                rs.getString("name"),
                trim(rs.getString("teacher")),
                trim(rs.getString("location")),
                rs.getInt("day_of_week"),
                rs.getTime("start_time").toLocalTime(),
                rs.getTime("end_time").toLocalTime()
        ), sno);
    }

    /**
     * 给前端选课清单做冲突预检：cno -> [{d,s,e}, ...] 的 JSON。
     */
    public String sessionJson() {
        Map<String, List<Map<String, Object>>> sessions = new LinkedHashMap<>();
        jdbc.query("""
                select cno, day_of_week,
                       substring(start_time::text from 1 for 5) as start_time,
                       substring(end_time::text from 1 for 5) as end_time
                from course_sessions
                where cno in (select cno from courses where is_active)
                order by cno, day_of_week, start_time
                """, (RowCallbackHandler) rs -> {
            String cno = rs.getString("cno").trim();
            List<Map<String, Object>> courseSessions =
                    sessions.computeIfAbsent(cno, key -> new ArrayList<>());
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("d", rs.getInt("day_of_week"));
            session.put("s", rs.getString("start_time"));
            session.put("e", rs.getString("end_time"));
            courseSessions.add(session);
        });
        try {
            return JSON.writeValueAsString(sessions);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    public List<StudentRow> listEnrolledStudents(String cno) {
        return jdbc.query("""
                select s.sno, s.name, s.grade, s.major, s.college, s.phone
                from enrollments e
                join students s on s.sno = e.sno
                where e.cno = ?
                order by s.sno
                """, (rs, rowNum) -> new StudentRow(
                rs.getString("sno").trim(),
                rs.getString("name"),
                trim(rs.getString("grade")),
                trim(rs.getString("major")),
                trim(rs.getString("college")),
                trim(rs.getString("phone"))
        ), cno);
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }
}
