package com.example.choosecourse.service;

import com.example.choosecourse.web.dto.CourseRow;
import com.example.choosecourse.web.dto.CourseSession;
import com.example.choosecourse.web.dto.RecommendationResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 全局智能推荐：跨分页分析全部可选课程，而不是只看当前页。
 *
 * 规则：
 * 1. 排除已选、已修、已满、先修未满足、与已选课程时间冲突的课程；
 * 2. 在剩余学分内按学分从高到低挑选；
 * 3. 推荐课程之间也不能时间冲突；
 * 4. 最终提交仍由 EnrollmentService 逐门做权威校验。
 */
@Service
public class RecommendationService {

    private final CourseQueryService courseQueryService;

    public RecommendationService(CourseQueryService courseQueryService) {
        this.courseQueryService = courseQueryService;
    }

    public RecommendationResult recommend(String sno, int maxCredits) {
        List<CourseRow> allCourses = courseQueryService.listForStudent(sno);
        int selectedCredits = courseQueryService.listSelected(sno).stream()
                .mapToInt(CourseRow::getCredits)
                .sum();
        int availableCredits = Math.max(0, maxCredits - selectedCredits);
        if (availableCredits == 0) {
            return new RecommendationResult(List.of(), selectedCredits, 0,
                    availableCredits, maxCredits);
        }

        List<CourseRow> candidates = allCourses.stream()
                .filter(course -> !course.isSelected())
                .filter(course -> !course.isCompleted())
                .filter(course -> !course.isFull())
                .filter(course -> !course.isPrerequisiteMissing())
                .filter(course -> !course.isConflict())
                .filter(course -> course.getCredits() > 0)
                .filter(course -> course.getCredits() <= availableCredits)
                .sorted(Comparator.comparingInt(CourseRow::getCredits).reversed()
                        .thenComparing(Comparator.comparingInt(CourseRow::getRemainingSeats)
                                .reversed())
                        .thenComparing(CourseRow::getCno))
                .toList();

        Map<String, List<CourseSession>> sessions = courseQueryService.listActiveSessions();
        List<CourseRow> recommended = new ArrayList<>();
        int usedCredits = 0;
        for (CourseRow candidate : candidates) {
            if (usedCredits + candidate.getCredits() > availableCredits) {
                continue;
            }
            if (conflictsWithRecommended(candidate, recommended, sessions)) {
                continue;
            }
            recommended.add(candidate);
            usedCredits += candidate.getCredits();
        }

        return new RecommendationResult(recommended, selectedCredits, usedCredits,
                availableCredits, maxCredits);
    }

    private boolean conflictsWithRecommended(CourseRow candidate, List<CourseRow> recommended,
                                             Map<String, List<CourseSession>> sessions) {
        List<CourseSession> candidateSessions = sessions.getOrDefault(candidate.getCno(), List.of());
        if (candidateSessions.isEmpty()) {
            return false;
        }
        for (CourseRow other : recommended) {
            for (CourseSession a : candidateSessions) {
                for (CourseSession b : sessions.getOrDefault(other.getCno(), List.of())) {
                    if (a.dayOfWeek() == b.dayOfWeek()
                            && a.startTime().isBefore(b.endTime())
                            && b.startTime().isBefore(a.endTime())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
