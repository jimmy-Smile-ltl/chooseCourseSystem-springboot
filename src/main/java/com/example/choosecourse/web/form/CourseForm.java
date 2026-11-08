package com.example.choosecourse.web.form;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

public class CourseForm {

    @NotBlank(message = "课程号不能为空")
    @Size(max = 20, message = "课程号最长 20 个字符")
    private String cno;

    @NotBlank(message = "课程名称不能为空")
    @Size(max = 100, message = "课程名称最长 100 个字符")
    private String name;

    @Size(max = 50, message = "教师名最长 50 个字符")
    private String teacher;

    @Size(max = 50, message = "地点最长 50 个字符")
    private String location;

    @Min(value = 0, message = "容量不能小于 0")
    private int capacity;

    @Min(value = 0, message = "学分不能小于 0")
    @Max(value = 12, message = "学分不能超过 12")
    private int credits = 2;

    private boolean active = true;

    @NotNull(message = "请选择上课星期")
    @Min(value = 1, message = "星期范围 1-7")
    @Max(value = 7, message = "星期范围 1-7")
    private Integer dayOfWeek = 1;

    @NotNull(message = "请选择开始时间")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime startTime = LocalTime.of(8, 30);

    @NotNull(message = "请选择结束时间")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime endTime = LocalTime.of(10, 10);

    private List<String> prerequisiteCnos = new ArrayList<>();

    public String getCno() {
        return cno;
    }

    public void setCno(String cno) {
        this.cno = cno;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTeacher() {
        return teacher;
    }

    public void setTeacher(String teacher) {
        this.teacher = teacher;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public int getCredits() {
        return credits;
    }

    public void setCredits(int credits) {
        this.credits = credits;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getDayOfWeek() {
        return dayOfWeek;
    }

    public void setDayOfWeek(Integer dayOfWeek) {
        this.dayOfWeek = dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.endTime = endTime;
    }

    public List<String> getPrerequisiteCnos() {
        return prerequisiteCnos;
    }

    public void setPrerequisiteCnos(List<String> prerequisiteCnos) {
        this.prerequisiteCnos = prerequisiteCnos == null ? new ArrayList<>() : prerequisiteCnos;
    }
}
