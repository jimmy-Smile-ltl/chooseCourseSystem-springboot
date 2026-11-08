package com.example.choosecourse.web.dto;

import java.time.LocalTime;

public class ScheduleEntry {

    private final String cno;
    private final String name;
    private final String teacher;
    private final String location;
    private final int dayOfWeek;
    private final LocalTime startTime;
    private final LocalTime endTime;

    public ScheduleEntry(String cno, String name, String teacher, String location,
                         int dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.cno = cno;
        this.name = name;
        this.teacher = teacher;
        this.location = location;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getCno() {
        return cno;
    }

    public String getName() {
        return name;
    }

    public String getTeacher() {
        return teacher;
    }

    public String getLocation() {
        return location;
    }

    public int getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }
}
