package com.example.choosecourse.web.dto;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CourseRowTest {

    @Test
    void fullWhenSelectedCountReachesCapacity() {
        assertTrue(row(2, 2).isFull());
        assertFalse(row(2, 1).isFull());
    }

    @Test
    void fullWhenSelectedCountExceedsCapacity() {
        assertTrue(row(2, 3).isFull());
    }

    private CourseRow row(int capacity, int selectedCount) {
        return new CourseRow("c001", "测试课程", "教师", "A101",
                capacity, 2, selectedCount, Math.max(0, capacity - selectedCount),
                "周一 08:30-10:10", "-",
                true, false, false, false, false);
    }
}
