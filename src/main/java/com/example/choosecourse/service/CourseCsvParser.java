package com.example.choosecourse.service;

import com.example.choosecourse.web.dto.CourseImportError;
import org.springframework.stereotype.Component;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 课程 CSV 解析与格式校验。只做纯文本校验，不访问数据库；
 * 课程号是否已存在、先修课是否存在由 {@link CourseImportService} 校验。
 */
@Component
public class CourseCsvParser {

    public static final int MAX_COURSES = 200;
    public static final int MAX_ROWS = 1000;
    private static final int MAX_ERRORS = 100;
    private static final long MAX_BYTES = 1_000_000L;
    private static final Pattern CNO_PATTERN = Pattern.compile("[A-Za-z0-9_-]{1,20}");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("H:mm");

    private static final Map<String, String> HEADER_ALIASES = Map.ofEntries(
            Map.entry("课程号", "cno"),
            Map.entry("cno", "cno"),
            Map.entry("课程名称", "name"),
            Map.entry("name", "name"),
            Map.entry("教师", "teacher"),
            Map.entry("teacher", "teacher"),
            Map.entry("地点", "location"),
            Map.entry("location", "location"),
            Map.entry("容量", "capacity"),
            Map.entry("capacity", "capacity"),
            Map.entry("学分", "credits"),
            Map.entry("credits", "credits"),
            Map.entry("星期", "day"),
            Map.entry("上课星期", "day"),
            Map.entry("day", "day"),
            Map.entry("day_of_week", "day"),
            Map.entry("开始时间", "start"),
            Map.entry("start_time", "start"),
            Map.entry("start", "start"),
            Map.entry("结束时间", "end"),
            Map.entry("end_time", "end"),
            Map.entry("end", "end"),
            Map.entry("先修课程号", "prereq"),
            Map.entry("先修课", "prereq"),
            Map.entry("prerequisite", "prereq"),
            Map.entry("prerequisite_cno", "prereq"),
            Map.entry("是否开放", "active"),
            Map.entry("开放", "active"),
            Map.entry("active", "active")
    );

    private static final Map<String, String> DISPLAY_NAMES = Map.of(
            "cno", "课程号",
            "name", "课程名称",
            "capacity", "容量",
            "credits", "学分",
            "day", "星期",
            "start", "开始时间",
            "end", "结束时间"
    );

    private static final List<String> REQUIRED_COLUMNS = List.of(
            "cno", "name", "capacity", "credits", "day", "start", "end");

    public record Session(int dayOfWeek, LocalTime start, LocalTime end) {
    }

    public record ImportCourse(int line, String cno, String name, String teacher, String location,
                               int capacity, int credits, boolean active,
                               List<String> prerequisiteCnos, List<Session> sessions) {
        public ImportCourse {
            prerequisiteCnos = List.copyOf(prerequisiteCnos);
            sessions = List.copyOf(sessions);
        }
    }

    public record ParseResult(List<ImportCourse> courses, List<CourseImportError> errors) {
        public ParseResult {
            courses = List.copyOf(courses);
            errors = List.copyOf(errors);
        }
    }

    public ParseResult parse(byte[] content) {
        Errors errors = new Errors();
        if (content == null || content.length == 0) {
            errors.add(0, "文件为空");
            return new ParseResult(List.of(), errors.list());
        }
        if (content.length > MAX_BYTES) {
            errors.add(0, "文件不能超过 1MB");
            return new ParseResult(List.of(), errors.list());
        }

        String text;
        try {
            text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException e) {
            errors.add(0, "文件不是有效的 UTF-8 编码，请另存为“CSV UTF-8”");
            return new ParseResult(List.of(), errors.list());
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);
        }

        List<CsvRecord> records = readRecords(text, errors);
        records.removeIf(record -> record.fields().stream().allMatch(String::isBlank));
        if (records.isEmpty()) {
            errors.add(0, "没有可导入的数据");
            return new ParseResult(List.of(), errors.list());
        }

        CsvRecord headerRecord = records.getFirst();
        Map<String, Integer> columns = parseHeader(headerRecord, errors);
        if (!errors.isEmpty()) {
            return new ParseResult(List.of(), errors.list());
        }

        int headerWidth = headerRecord.fields().size();
        Map<String, CourseBuilder> builders = new LinkedHashMap<>();
        int dataRows = 0;
        for (int i = 1; i < records.size(); i++) {
            CsvRecord record = records.get(i);
            if (++dataRows > MAX_ROWS) {
                errors.add(record.line(), "最多支持 " + MAX_ROWS + " 行数据");
                break;
            }
            parseRow(record, columns, headerWidth, builders, errors);
        }

        List<ImportCourse> courses = builders.values().stream()
                .map(CourseBuilder::toCourse)
                .toList();
        if (errors.isEmpty() && courses.isEmpty()) {
            errors.add(0, "没有可导入的课程");
        }
        return new ParseResult(courses, errors.list());
    }

    private Map<String, Integer> parseHeader(CsvRecord headerRecord, Errors errors) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        List<String> headers = headerRecord.fields();
        for (int i = 0; i < headers.size(); i++) {
            String header = headers.get(i).trim();
            if (header.isEmpty()) {
                errors.add(headerRecord.line(), "第 " + (i + 1) + " 列的表头为空");
                continue;
            }
            String canonical = HEADER_ALIASES.get(header);
            if (canonical == null) {
                errors.add(headerRecord.line(), "未知列：“" + header + "”");
            } else if (!seen.add(canonical)) {
                errors.add(headerRecord.line(), "重复列：“" + header + "”");
            } else {
                columns.put(canonical, i);
            }
        }
        for (String required : REQUIRED_COLUMNS) {
            if (!columns.containsKey(required)) {
                errors.add(headerRecord.line(), "缺少必需列：" + DISPLAY_NAMES.get(required));
            }
        }
        return columns;
    }

    private void parseRow(CsvRecord record, Map<String, Integer> columns, int headerWidth,
                          Map<String, CourseBuilder> builders, Errors errors) {
        List<String> fields = record.fields();
        if (fields.size() != headerWidth) {
            errors.add(record.line(), "列数不正确：表头有 " + headerWidth + " 列，当前行有 "
                    + fields.size() + " 列");
            return;
        }

        List<String> rowErrors = new ArrayList<>();
        String cno = value(fields, columns, "cno");
        String name = value(fields, columns, "name");
        String teacher = value(fields, columns, "teacher");
        String location = value(fields, columns, "location");

        if (cno.isEmpty()) {
            rowErrors.add("课程号不能为空");
        } else if (!CNO_PATTERN.matcher(cno).matches()) {
            rowErrors.add("课程号只能包含字母、数字、下划线或短横线，长度 1-20");
        }
        if (name.isEmpty()) {
            rowErrors.add("课程名称不能为空");
        } else if (name.length() > 100) {
            rowErrors.add("课程名称不能超过 100 个字符");
        }
        if (teacher.length() > 100) {
            rowErrors.add("教师不能超过 100 个字符");
        }
        if (location.length() > 100) {
            rowErrors.add("地点不能超过 100 个字符");
        }

        Integer capacity = parseInt(value(fields, columns, "capacity"), rowErrors,
                "容量", 0, 10_000);
        Integer credits = parseInt(value(fields, columns, "credits"), rowErrors,
                "学分", 0, 12);
        Integer day = parseDay(value(fields, columns, "day"), rowErrors);
        LocalTime start = parseTime(value(fields, columns, "start"), "开始时间", rowErrors);
        LocalTime end = parseTime(value(fields, columns, "end"), "结束时间", rowErrors);
        if (start != null && end != null && !start.isBefore(end)) {
            rowErrors.add("开始时间必须早于结束时间");
        }
        Boolean active = parseActive(value(fields, columns, "active"), rowErrors);
        List<String> prerequisites = parsePrerequisites(value(fields, columns, "prereq"), cno, rowErrors);

        for (String message : rowErrors) {
            errors.add(record.line(), message);
        }
        if (!rowErrors.isEmpty()) {
            return;
        }

        CourseBuilder builder = builders.get(cno);
        if (builder == null) {
            if (builders.size() >= MAX_COURSES) {
                errors.add(record.line(), "一次最多导入 " + MAX_COURSES + " 门课程");
                return;
            }
            builder = new CourseBuilder(record.line(), cno, name, teacher, location,
                    capacity, credits, active, prerequisites);
            builders.put(cno, builder);
        } else if (!builder.matches(name, teacher, location, capacity, credits,
                active, prerequisites)) {
            errors.add(record.line(), "课程号 " + cno + " 重复，但课程名称/教师/地点/容量/学分/"
                    + "先修课/开放状态与第 " + builder.line + " 行不一致");
            return;
        }

        Session session = new Session(day, start, end);
        if (builder.hasOverlappingSession(session)) {
            errors.add(record.line(), "课程 " + cno + " 的上课时段重叠或重复");
            return;
        }
        builder.sessions.add(session);
    }

    private static String value(List<String> fields, Map<String, Integer> columns, String key) {
        Integer index = columns.get(key);
        if (index == null || index >= fields.size()) {
            return "";
        }
        return fields.get(index).trim();
    }

    private static Integer parseInt(String raw, List<String> errors,
                                    String label, int min, int max) {
        if (raw.isEmpty()) {
            errors.add(label + "不能为空");
            return null;
        }
        try {
            int value = Integer.parseInt(raw);
            if (value < min || value > max) {
                errors.add(label + "必须在 " + min + "-" + max + " 之间");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            errors.add(label + "必须是整数");
            return null;
        }
    }

    private static Integer parseDay(String raw, List<String> errors) {
        Integer day = switch (raw) {
            case "1", "周一", "星期一", "一" -> 1;
            case "2", "周二", "星期二", "二" -> 2;
            case "3", "周三", "星期三", "三" -> 3;
            case "4", "周四", "星期四", "四" -> 4;
            case "5", "周五", "星期五", "五" -> 5;
            case "6", "周六", "星期六", "六" -> 6;
            case "7", "周日", "周天", "星期日", "星期天", "日", "天", "七" -> 7;
            default -> null;
        };
        if (day == null) {
            errors.add("星期必须是 1-7 或 周一…周日");
        }
        return day;
    }

    private static LocalTime parseTime(String raw, String label, List<String> errors) {
        if (raw.isEmpty()) {
            errors.add(label + "不能为空");
            return null;
        }
        try {
            return LocalTime.parse(raw, TIME_FORMAT);
        } catch (DateTimeParseException e) {
            errors.add(label + "格式应为 HH:mm（24 小时制）");
            return null;
        }
    }

    private static Boolean parseActive(String raw, List<String> errors) {
        if (raw.isEmpty()) {
            return true;
        }
        return switch (raw.toLowerCase()) {
            case "是", "开放", "true", "1" -> true;
            case "否", "停开", "false", "0" -> false;
            default -> {
                errors.add("是否开放只能是 是/否、开放/停开、true/false 或 1/0");
                yield null;
            }
        };
    }

    private static List<String> parsePrerequisites(String raw, String cno, List<String> errors) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (raw.isEmpty()) {
            return List.of();
        }
        for (String part : raw.split("[|、;；,，]")) {
            String prerequisite = part.trim();
            if (prerequisite.isEmpty()) {
                continue;
            }
            if (!CNO_PATTERN.matcher(prerequisite).matches()) {
                errors.add("先修课程号格式不正确：" + prerequisite);
            } else if (prerequisite.equals(cno)) {
                errors.add("先修课不能是课程本身");
            } else {
                result.add(prerequisite);
            }
        }
        return List.copyOf(result);
    }

    private List<CsvRecord> readRecords(String text, Errors errors) {
        List<CsvRecord> records = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        int line = 1;
        int recordStart = 1;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    if (c == '\n') {
                        line++;
                    }
                    field.append(c);
                }
            } else if (c == '"') {
                if (field.length() == 0) {
                    quoted = true;
                } else {
                    errors.add(line, "引号必须出现在字段开头");
                    field.append(c);
                }
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (c == '\r') {
                // 兼容 CRLF / CR
            } else if (c == '\n') {
                fields.add(field.toString());
                field.setLength(0);
                records.add(new CsvRecord(recordStart, fields));
                fields = new ArrayList<>();
                line++;
                recordStart = line;
            } else {
                field.append(c);
            }
        }

        if (quoted) {
            errors.add(recordStart, "引号未闭合");
        }
        if (field.length() > 0 || !fields.isEmpty()) {
            fields.add(field.toString());
            records.add(new CsvRecord(recordStart, fields));
        }
        return records;
    }

    private record CsvRecord(int line, List<String> fields) {
        private CsvRecord {
            fields = List.copyOf(fields);
        }
    }

    private static final class CourseBuilder {
        private final int line;
        private final String cno;
        private final String name;
        private final String teacher;
        private final String location;
        private final int capacity;
        private final int credits;
        private final boolean active;
        private final List<String> prerequisiteCnos;
        private final List<Session> sessions = new ArrayList<>();

        private CourseBuilder(int line, String cno, String name, String teacher, String location,
                              int capacity, int credits, boolean active,
                              List<String> prerequisiteCnos) {
            this.line = line;
            this.cno = cno;
            this.name = name;
            this.teacher = teacher;
            this.location = location;
            this.capacity = capacity;
            this.credits = credits;
            this.active = active;
            this.prerequisiteCnos = prerequisiteCnos;
        }

        private boolean matches(String name, String teacher, String location,
                                int capacity, int credits, boolean active,
                                List<String> prerequisiteCnos) {
            return this.name.equals(name)
                    && this.teacher.equals(teacher)
                    && this.location.equals(location)
                    && this.capacity == capacity
                    && this.credits == credits
                    && this.active == active
                    && new HashSet<>(this.prerequisiteCnos).equals(new HashSet<>(prerequisiteCnos));
        }

        private boolean hasOverlappingSession(Session candidate) {
            return sessions.stream().anyMatch(existing ->
                    existing.dayOfWeek() == candidate.dayOfWeek()
                            && candidate.start().isBefore(existing.end())
                            && existing.start().isBefore(candidate.end()));
        }

        private ImportCourse toCourse() {
            return new ImportCourse(line, cno, name, teacher, location,
                    capacity, credits, active, prerequisiteCnos, sessions);
        }
    }

    private static final class Errors {
        private final List<CourseImportError> errors = new ArrayList<>();
        private boolean truncated;

        private void add(int line, String message) {
            if (errors.size() < MAX_ERRORS) {
                errors.add(new CourseImportError(line, message));
            } else {
                truncated = true;
            }
        }

        private boolean isEmpty() {
            return errors.isEmpty();
        }

        private List<CourseImportError> list() {
            if (!truncated) {
                return errors;
            }
            List<CourseImportError> result = new ArrayList<>(errors);
            result.add(new CourseImportError(0, "错误过多，仅显示前 " + MAX_ERRORS + " 条"));
            return result;
        }
    }
}
