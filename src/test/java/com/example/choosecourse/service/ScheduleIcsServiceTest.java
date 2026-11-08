package com.example.choosecourse.service;

import com.example.choosecourse.web.dto.ScheduleEntry;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ScheduleIcsServiceTest {

    @Test
    void exportsCalendarEvent() {
        ScheduleEntry entry = new ScheduleEntry(
                "t001", "数据结构", "张老师", "A101", 1,
                LocalTime.of(8, 30), LocalTime.of(10, 10));

        String ics = new ScheduleIcsService().build("20200001", List.of(entry));

        assertTrue(ics.contains("BEGIN:VCALENDAR"));
        assertTrue(ics.contains("BEGIN:VEVENT"));
        assertTrue(ics.contains("SUMMARY:数据结构"));
        assertTrue(ics.contains("LOCATION:A101"));
        assertTrue(ics.contains("RRULE:FREQ=WEEKLY;COUNT=16"));
        assertTrue(ics.contains("END:VEVENT"));
        assertTrue(ics.contains("END:VCALENDAR"));
    }
}
