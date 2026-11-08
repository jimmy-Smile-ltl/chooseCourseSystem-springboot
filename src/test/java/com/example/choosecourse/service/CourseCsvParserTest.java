package com.example.choosecourse.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseCsvParserTest {

    private static final String HEADER =
            "课程号,课程名称,教师,地点,容量,学分,星期,开始时间,结束时间,先修课程号,是否开放\r\n";

    private final CourseCsvParser parser = new CourseCsvParser();

    @Test
    void parsesValidCsvAndMergesSessions() {
        String csv = "\uFEFF" + HEADER
                + "cs101,\"数据结构, 进阶\",张老师,A101,60,4,周一,08:30,10:10,,是\r\n"
                + "cs101,\"数据结构, 进阶\",张老师,A101,60,4,周三,8:30,10:10,,是\r\n"
                + "cs102,操作系统,李老师,B202,40,3,2,13:00,15:35,cs101|cs101,否\r\n";

        var result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertTrue(result.errors().isEmpty(), () -> result.errors().toString());
        assertEquals(2, result.courses().size());

        var first = result.courses().stream()
                .filter(course -> course.cno().equals("cs101"))
                .findFirst().orElseThrow();
        assertEquals("数据结构, 进阶", first.name());
        assertEquals(2, first.sessions().size());
        assertEquals(LocalTime.of(8, 30), first.sessions().get(1).start());

        var second = result.courses().stream()
                .filter(course -> course.cno().equals("cs102"))
                .findFirst().orElseThrow();
        assertFalse(second.active());
        assertEquals(List.of("cs101"), second.prerequisiteCnos());
    }

    @Test
    void collectsRowErrors() {
        String csv = HEADER
                + "cs101,,张老师,A101,abc,13,周八,25:00,08:00,cs101,maybe\r\n";

        var result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        assertTrue(result.courses().isEmpty());
        List<String> messages = result.errors().stream()
                .map(error -> error.getMessage())
                .toList();
        assertTrue(messages.stream().anyMatch(m -> m.contains("课程名称不能为空")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("容量必须是整数")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("学分必须在 0-12 之间")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("星期必须是")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("开始时间格式")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("先修课不能是课程本身")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("是否开放")));
    }

    @Test
    void rejectsBadHeaderAndUtf8() {
        var headerResult = parser.parse(
                "课程号,课程名称,容量,学分,星期,开始时间,结束时间,未知列\r\n".getBytes(StandardCharsets.UTF_8));
        assertTrue(headerResult.errors().stream()
                .anyMatch(error -> error.getMessage().contains("未知列")));

        var utf8Result = parser.parse(new byte[]{(byte) 0xC4, (byte) 0xE3});
        assertTrue(utf8Result.errors().stream()
                .anyMatch(error -> error.getMessage().contains("UTF-8")));
    }

    @Test
    void rejectsInconsistentDuplicateAndOverlappingSessions() {
        String csv = HEADER
                + "cs101,课程,张老师,A101,60,3,周一,08:30,10:10,,是\r\n"
                + "cs101,课程,张老师,A101,50,3,周二,08:30,10:10,,是\r\n"
                + "cs101,课程,张老师,A101,60,3,周一,09:00,11:00,,是\r\n";

        var result = parser.parse(csv.getBytes(StandardCharsets.UTF_8));

        List<String> messages = result.errors().stream()
                .map(error -> error.getMessage())
                .toList();
        assertTrue(messages.stream().anyMatch(m -> m.contains("不一致")));
        assertTrue(messages.stream().anyMatch(m -> m.contains("上课时段重叠")));
    }
}
