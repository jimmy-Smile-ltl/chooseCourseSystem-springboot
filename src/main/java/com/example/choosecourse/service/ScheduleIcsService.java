package com.example.choosecourse.service;

import com.example.choosecourse.web.dto.ScheduleEntry;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
public class ScheduleIcsService {

    private static final DateTimeFormatter ICS_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Shanghai");

    public String build(String studentName, List<ScheduleEntry> entries) {
        LocalDate monday = LocalDate.now(SCHOOL_ZONE)
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

        StringBuilder ics = new StringBuilder(2048);
        line(ics, "BEGIN:VCALENDAR");
        line(ics, "VERSION:2.0");
        line(ics, "PRODID:-//ChooseCourse//CN");
        line(ics, "CALSCALE:GREGORIAN");
        line(ics, "METHOD:PUBLISH");
        line(ics, "X-WR-CALNAME:选课课表 - " + escape(studentName));

        for (ScheduleEntry entry : entries) {
            LocalDate date = monday.plusDays(entry.getDayOfWeek() - 1L);
            LocalDateTime start = LocalDateTime.of(date, entry.getStartTime());
            LocalDateTime end = LocalDateTime.of(date, entry.getEndTime());

            line(ics, "BEGIN:VEVENT");
            line(ics, "UID:" + escape(entry.getCno()) + "-"
                    + entry.getDayOfWeek() + "-" + entry.getStartTime() + "@choose-course");
            line(ics, "DTSTAMP:" + LocalDateTime.now(ZoneOffset.UTC).format(ICS_DATE_TIME) + "Z");
            line(ics, "DTSTART;TZID=Asia/Shanghai:" + start.format(ICS_DATE_TIME));
            line(ics, "DTEND;TZID=Asia/Shanghai:" + end.format(ICS_DATE_TIME));
            line(ics, "RRULE:FREQ=WEEKLY;COUNT=16");
            line(ics, "SUMMARY:" + escape(entry.getName()));
            line(ics, "LOCATION:" + escape(entry.getLocation()));
            line(ics, "DESCRIPTION:" + escape(entry.getTeacher() + " / " + entry.getCno()));
            line(ics, "END:VEVENT");
        }

        line(ics, "END:VCALENDAR");
        return ics.toString();
    }

    private static void line(StringBuilder builder, String value) {
        builder.append(value).append("\r\n");
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r", "")
                .replace("\n", "\\n");
    }
}
