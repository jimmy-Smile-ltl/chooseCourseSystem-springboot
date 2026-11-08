package com.example.choosecourse.service;

import com.example.choosecourse.web.dto.ScheduleEntry;
import com.example.choosecourse.web.dto.ScheduleRow;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleServiceTest {

    private final ScheduleService scheduleService = new ScheduleService();

    @Test
    void mapsTimesToPeriods() {
        assertEquals(1, scheduleService.periodOf(LocalTime.of(8, 30)));
        assertEquals(2, scheduleService.periodOf(LocalTime.of(9, 25)));
        assertEquals(2, scheduleService.periodOf(LocalTime.of(10, 10)));
        assertEquals(3, scheduleService.periodOf(LocalTime.of(11, 5)));
        assertEquals(4, scheduleService.periodOf(LocalTime.of(12, 0)));
        assertEquals(5, scheduleService.periodOf(LocalTime.of(13, 0)));
        assertEquals(7, scheduleService.periodOf(LocalTime.of(15, 35)));
        assertEquals(12, scheduleService.periodOf(LocalTime.of(21, 5)));
        assertEquals(1, scheduleService.periodOf(LocalTime.of(7, 0)));
    }

    @Test
    void buildsGridAcrossPeriodsAndDays() {
        List<ScheduleRow> rows = scheduleService.build(List.of(
                new ScheduleEntry("t001", "数据结构", "张老师", "A101", 1,
                        LocalTime.of(8, 30), LocalTime.of(10, 10)),
                new ScheduleEntry("t002", "操作系统", "李老师", "B202", 2,
                        LocalTime.of(10, 20), LocalTime.of(12, 0))
        ));

        assertEquals(12, rows.size());
        assertTrue(rows.get(0).getCells().get(0).contains("数据结构"));
        assertTrue(rows.get(1).getCells().get(0).contains("数据结构"));
        assertTrue(rows.get(2).getCells().get(1).contains("操作系统"));
        assertTrue(rows.get(3).getCells().get(1).contains("操作系统"));
        assertEquals("", rows.get(0).getCells().get(2));
    }

    @Test
    void buildsBlocksAndMarksConflicts() {
        var blocks = scheduleService.buildBlocks(List.of(
                new ScheduleEntry("t001", "数据结构", "张老师", "A101", 1,
                        LocalTime.of(8, 30), LocalTime.of(10, 10)),
                new ScheduleEntry("t002", "操作系统", "李老师", "B202", 1,
                        LocalTime.of(9, 0), LocalTime.of(10, 0))
        ));

        assertTrue(blocks.size() == 2);
        assertTrue(blocks.stream().allMatch(block -> block.isConflict()));
        assertTrue(blocks.stream().allMatch(block -> block.getStartPeriod() == 1));
        assertTrue(blocks.stream().allMatch(block -> block.getEndPeriod() == 2));
    }

    @Test
    void appendsConflictingCoursesInSameCell() {
        List<ScheduleRow> rows = scheduleService.build(List.of(
                new ScheduleEntry("t001", "数据结构", "张老师", "A101", 1,
                        LocalTime.of(8, 30), LocalTime.of(10, 10)),
                new ScheduleEntry("t002", "操作系统", "李老师", "B202", 1,
                        LocalTime.of(8, 30), LocalTime.of(10, 10))
        ));

        String cell = rows.get(0).getCells().get(0);
        assertTrue(cell.contains("数据结构"));
        assertTrue(cell.contains("操作系统"));
    }
}
