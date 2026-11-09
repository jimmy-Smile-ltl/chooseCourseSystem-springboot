package com.example.choosecourse.web.dto;

import java.time.LocalTime;

public record CourseSession(int dayOfWeek, LocalTime startTime, LocalTime endTime) {
}
