package com.example.choosecourse.web.dto;

public class ScheduleBlock {

    private final String cno;
    private final String name;
    private final String teacher;
    private final String location;
    private final int dayOfWeek;
    private final int startPeriod;
    private final int endPeriod;
    private final int colorIndex;
    private boolean conflict;

    public ScheduleBlock(String cno, String name, String teacher, String location,
                         int dayOfWeek, int startPeriod, int endPeriod, int colorIndex) {
        this.cno = cno;
        this.name = name;
        this.teacher = teacher;
        this.location = location;
        this.dayOfWeek = dayOfWeek;
        this.startPeriod = startPeriod;
        this.endPeriod = endPeriod;
        this.colorIndex = colorIndex;
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

    public int getStartPeriod() {
        return startPeriod;
    }

    public int getEndPeriod() {
        return endPeriod;
    }

    public int getColorIndex() {
        return Math.floorMod(colorIndex, 8);
    }

    public boolean isConflict() {
        return conflict;
    }

    public void setConflict(boolean conflict) {
        this.conflict = conflict;
    }
}
