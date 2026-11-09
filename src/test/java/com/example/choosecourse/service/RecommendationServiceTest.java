package com.example.choosecourse.service;

import com.example.choosecourse.web.dto.CourseRow;
import com.example.choosecourse.web.dto.CourseSession;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecommendationServiceTest {

    @Test
    void recommendsAcrossAllCoursesWithinCreditsAndAvoidsConflicts() {
        CourseQueryService query = mock(CourseQueryService.class);

        CourseRow selected = course("s", 10, 10, true, false, false, false);
        CourseRow d = course("d", 15, 10, false, false, false, false);
        CourseRow c = course("c", 6, 10, false, false, false, false);
        CourseRow a = course("a", 5, 10, false, false, false, false);
        CourseRow b = course("b", 5, 10, false, false, false, false);
        CourseRow prereqMissing = course("e", 4, 10, false, false, true, false);
        CourseRow full = course("f", 5, 0, false, false, false, false);
        CourseRow completed = course("g", 5, 10, false, true, false, false);

        when(query.listForStudent("20200001")).thenReturn(
                List.of(selected, d, c, a, b, prereqMissing, full, completed));
        when(query.listSelected("20200001")).thenReturn(List.of(selected));
        when(query.listActiveSessions()).thenReturn(Map.of(
                "d", List.of(new CourseSession(1, LocalTime.of(10, 0), LocalTime.of(12, 0))),
                "c", List.of(new CourseSession(2, LocalTime.of(8, 0), LocalTime.of(10, 0))),
                "a", List.of(new CourseSession(3, LocalTime.of(8, 0), LocalTime.of(10, 0))),
                "b", List.of(new CourseSession(3, LocalTime.of(9, 0), LocalTime.of(11, 0)))
        ));

        var result = new RecommendationService(query).recommend("20200001", 30);

        assertEquals(List.of("d", "a"),
                result.getCourses().stream().map(CourseRow::getCno).toList());
        assertEquals(10, result.getSelectedCredits());
        assertEquals(20, result.getAvailableCredits());
        assertEquals(20, result.getRecommendedCredits());
    }

    @Test
    void returnsEmptyWhenNoCreditsRemain() {
        CourseQueryService query = mock(CourseQueryService.class);
        CourseRow selected = course("s", 30, 10, true, false, false, false);
        when(query.listForStudent("20200001")).thenReturn(List.of(selected));
        when(query.listSelected("20200001")).thenReturn(List.of(selected));

        var result = new RecommendationService(query).recommend("20200001", 30);

        assertTrue(result.getCourses().isEmpty());
        assertEquals(0, result.getAvailableCredits());
        assertEquals(0, result.getRecommendedCredits());
    }

    private CourseRow course(String cno, int credits, int remaining, boolean selected,
                             boolean completed, boolean prerequisiteMissing, boolean conflict) {
        return new CourseRow(cno, "课程 " + cno, "教师", "A101",
                remaining + 10, credits, 0, remaining, "周一 08:30-10:10", "-",
                true, selected, completed, prerequisiteMissing, conflict);
    }
}
