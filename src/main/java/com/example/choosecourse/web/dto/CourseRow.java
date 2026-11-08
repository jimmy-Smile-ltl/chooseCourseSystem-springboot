package com.example.choosecourse.web.dto;

/**
 * 用 JavaBean 风格而不是 record，确保 Thymeleaf/SpEL 的属性访问稳定。
 */
public class CourseRow {

    private final String cno;
    private final String name;
    private final String teacher;
    private final String location;
    private final int capacity;
    private final int credits;
    private final int selectedCount;
    private final int remainingSeats;
    private final String timeText;
    private final String prerequisiteText;
    private final boolean active;
    private final boolean selected;
    private final boolean completed;
    private final boolean prerequisiteMissing;
    private final boolean conflict;

    public CourseRow(String cno, String name, String teacher, String location,
                     int capacity, int credits, int selectedCount, int remainingSeats,
                     String timeText, String prerequisiteText,
                     boolean active, boolean selected, boolean completed,
                     boolean prerequisiteMissing, boolean conflict) {
        this.cno = cno;
        this.name = name;
        this.teacher = teacher;
        this.location = location;
        this.capacity = capacity;
        this.credits = credits;
        this.selectedCount = selectedCount;
        this.remainingSeats = remainingSeats;
        this.timeText = timeText;
        this.prerequisiteText = prerequisiteText;
        this.active = active;
        this.selected = selected;
        this.completed = completed;
        this.prerequisiteMissing = prerequisiteMissing;
        this.conflict = conflict;
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

    public int getCapacity() {
        return capacity;
    }

    public int getCredits() {
        return credits;
    }

    public int getSelectedCount() {
        return selectedCount;
    }

    public int getRemainingSeats() {
        return remainingSeats;
    }

    public String getTimeText() {
        return timeText;
    }

    public String getPrerequisiteText() {
        return prerequisiteText;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isSelected() {
        return selected;
    }

    public boolean isCompleted() {
        return completed;
    }

    public boolean isPrerequisiteMissing() {
        return prerequisiteMissing;
    }

    public boolean isConflict() {
        return conflict;
    }

    public boolean isFull() {
        return remainingSeats <= 0;
    }
}
